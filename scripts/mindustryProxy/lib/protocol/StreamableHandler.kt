package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.Registry
import mindustryProxy.lib.packet.StreamBegin
import mindustryProxy.lib.packet.StreamChunk
import mindustryProxy.lib.packet.Streamable

class StreamableHandler : MessageToMessageCodec<StreamChunk, Streamable>() {
    private var lastId = 0
    private val streamableMap = mutableMapOf<Int, StreamBegin.StreamBuilder>()
    override fun decode(ctx: ChannelHandlerContext?, packet: StreamChunk, out: MutableList<Any>) {
        packet.retain()
        val (type, built) = synchronized(streamableMap) {
            val info = streamableMap[packet.id]
            if (info == null) {
                out.add(packet)
                return
            }
            info.tryBuild(packet)?.let {
                streamableMap.remove(info.id)
                info.type to it
            } ?: return
        }
        out.add(Registry.decodeWithId(type, built.stream))
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        super.channelRead(ctx, msg)
        if (msg is StreamBegin && msg.needBuild) {
            synchronized(streamableMap) {
                streamableMap[msg.id]?.release()
                streamableMap[msg.id] = StreamBegin.StreamBuilder(msg.id, msg.total, msg.type)
                if (streamableMap.size > 1)
                    Server.logger.warning("StreamableHandler: streamableMap.size(${streamableMap.size}) > 1")
            }
        }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: Streamable, out: MutableList<Any>) {
        lastId-- //origin game is increase from 0, here use negative to avoid mistake
        val cid = lastId
        val stream = msg.stream.slice()
        out.add(StreamBegin(cid, stream.readableBytes(), msg.factory.packetId!!))
        while (stream.isReadable)
            out.add(StreamChunk(cid, stream.readRetainedSlice(stream.readableBytes().coerceAtMost(512))))
        stream.release()
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        streamableMap.values.forEach { it.release() }
        super.channelInactive(ctx)
    }
}