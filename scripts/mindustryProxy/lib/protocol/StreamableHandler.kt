package mindustryProxy.lib.protocol

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import mindustryProxy.lib.packet.*

class StreamableHandler : ChannelDuplexHandler() {
    private var lastId = 0
    private val streamableMap = mutableMapOf<Int, StreamBegin.StreamBuilder>()
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val packet = msg as Packet
        if (packet is StreamChunk) {
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
                packet.release()
                info!!.built?.let {
                    ctx.fireChannelRead(Registry.decodeWithId(info!!.type, it.stream))
                }
                return
            }
        }
        ctx.fireChannelRead(packet)
        if (packet is StreamBegin && packet.needBuild) {
            streamableMap[packet.id]?.release()
            streamableMap[packet.id] = StreamBegin.StreamBuilder(packet.id, packet.total, packet.type)
        }
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
            msg.release()
        } else ctx.write(msg, promise)
    }
}