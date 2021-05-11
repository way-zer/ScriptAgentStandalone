package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramChannel
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.UDPServer
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.Registry

@ChannelHandler.Sharable
object PacketHandler : ChannelDuplexHandler() {
    class FailHandlePacket(packet: Packet, cause: Throwable) : Exception("Fail to handle $packet", cause)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val packet = when (msg) {
            is Packet -> msg
            is ByteBuf -> Registry.decode(msg).apply {
                udp = ctx.channel() is DatagramChannel
                msg.release()
            }
            else -> error("Error msg type: $msg")
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
                val out = ctx.alloc().buffer()
                Registry.encode(out, msg)
                ReferenceCountUtil.release(msg)
                if (msg.udp && ctx.channel() !is DatagramChannel)
                    try {
                        UDPServer.send(ctx.channel(), out)
                        promise.setSuccess()
                    } catch (e: Throwable) {
                        promise.setFailure(e)
                    }
                else
                    ctx.write(out, promise)
            }
            else -> ctx.write(msg, promise)
        }
    }
}