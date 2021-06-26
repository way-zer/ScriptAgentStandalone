package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import mindustryProxy.lib.packet.Registry
import mindustryProxy.lib.packet.StreamBegin
import mindustryProxy.lib.packet.StreamChunk
import mindustryProxy.lib.packet.Streamable

class StreamableHandler : MessageToMessageCodec<StreamChunk, Streamable>() {
    private var lastId = 0
    private val streamableMap = mutableMapOf<Int, StreamBegin.StreamBuilder>()
    override fun decode(ctx: ChannelHandlerContext?, packet: StreamChunk, out: MutableList<Any>) {
        var info: StreamBegin.StreamBuilder? = null
        val result = synchronized(streamableMap) {
            info = streamableMap[packet.id] ?: return@synchronized false
            info!!.tryBuild(packet).also {
                if (it && info!!.built != null) {
                    streamableMap.remove(packet.id)
                }
            }
        }
        if (result) {
            info!!.built?.let {
                out.add(Registry.decodeWithId(info!!.type, it.stream))
            }
            return
        }
        packet.retain()
        out.add(packet)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        super.channelRead(ctx, msg)
        if (msg is StreamBegin && msg.needBuild) {
            synchronized(streamableMap) {
                streamableMap[msg.id]?.release()
                streamableMap[msg.id] = StreamBegin.StreamBuilder(msg.id, msg.total, msg.type)
            }
        }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: Streamable, out: MutableList<Any>) {
        lastId-- //origin game is increase from 0, here use negative to avoid mistake
        val cid = lastId
        val stream = msg.stream.slice()
        out.add(StreamBegin(cid, stream.readableBytes(), msg.factory.packetId!!))
        while (stream.readableBytes() > 512)
            out.add(StreamChunk(cid, stream.readSlice(512)))
        out.add(StreamChunk(cid, stream.readRetainedSlice(stream.readableBytes())))
    }
}