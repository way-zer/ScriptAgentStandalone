package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket

@ChannelHandler.Sharable
object UDPUnpack : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val data = (msg as DatagramPacket).content()
        data as ByteBuf
        ctx.fireChannelRead(data)
    }
}