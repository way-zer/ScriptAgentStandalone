package mindustryProxy.lib.protocol

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import mindustryProxy.lib.packet.*

class StreamableHandler : ChannelDuplexHandler() {
    private var lastId = 0
    private val streamableMap = mutableMapOf<Int, StreamBegin>()
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val packet = msg as Packet
        if (packet is StreamChunk) {
            val info = streamableMap[packet.id]
            val result = info?.tryBuild(packet)
            if (result != null) {
                ctx.fireChannelRead(Registry.decodeWithId(info.type, result.stream))
                return
            }
        }
        ctx.fireChannelRead(packet)
        if (packet is StreamBegin && packet.needBuild)
            streamableMap[packet.id] = packet
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is Streamable) {
            lastId-- //origin game is increase from 0, here use negative to avoid mistake
            val cid = lastId
            val stream = msg.stream.slice()
            ctx.write(StreamBegin(cid, stream.readableBytes(), msg.factory.packetId!!))
            while (stream.readableBytes() > 512)
                ctx.write(StreamChunk(cid, stream.readSlice(512)))
            ctx.write(StreamChunk(cid, stream.readRetainedSlice(stream.readableBytes())), promise)
        } else ctx.write(msg, promise)
    }
}