package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object UDPMultiplex : ChannelInboundHandlerAdapter() {
    private lateinit var ctx: ChannelHandlerContext
    private val bound = ConcurrentHashMap<InetSocketAddress, SubHandler>()
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.ctx = ctx
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        this.bound.clear()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as DatagramPacket
        val child = bound[msg.sender()] ?: return super.channelRead(ctx, msg)
        child.onUdpRead(msg.content())
    }

    fun send(packet: DatagramPacket) {
        ctx.writeAndFlush(packet)
    }

    class SubHandler(private val address: InetSocketAddress) : MultiplexHandler() {
        override fun handlerAdded(ctx: ChannelHandlerContext) {
            super.handlerAdded(ctx)
            if (bound.containsKey(address)) error("$address Already bind UDPChannel")
            bound[address] = this
        }

        override fun writeUdp(msg: ByteBuf) = send(DatagramPacket(msg, address))

        override fun handlerRemoved(ctx: ChannelHandlerContext) {
            super.handlerRemoved(ctx)
            bound.remove(address, this)
        }
    }
}