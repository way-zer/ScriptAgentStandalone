package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramChannel
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.Registry

@ChannelHandler.Sharable
object PacketHandler : ChannelDuplexHandler() {
    class FailHandlePacket(packet: Packet, cause: Throwable) : Exception("Fail to handle $packet", cause)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val packet = if (msg is Packet) msg
        else Registry.decode(msg as ByteBuf).apply {
            udp = ctx.channel() is DatagramChannel
        }
        try {
            ctx.fireChannelRead(packet)
        } catch (e: Throwable) {
            throw FailHandlePacket(packet, e)
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        when (msg) {
            is Packet -> {
                val out = ctx.alloc().buffer().retain()
                Registry.encode(out, msg)
                if (msg.udp && ctx.channel() !is DatagramChannel)
                    try {
                        Server.Udp.send(ctx.channel(), out)
                        promise.setSuccess()
                    } catch (e: Throwable) {
                        promise.setFailure(e)
                    }
                else
                    ctx.write(out, promise)
                out.release()
            }
            else -> ctx.write(msg, promise)
        }
    }
}