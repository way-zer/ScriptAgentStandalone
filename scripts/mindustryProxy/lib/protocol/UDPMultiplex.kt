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
    private val lastRead = mutableSetOf<SubHandler>()

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

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        super.channelReadComplete(ctx)
        lastRead.forEach { it.onUdpReadComplete() }
        lastRead.clear()
        ctx.flush()
    }

    fun sendSingle(packet: DatagramPacket) {
        ctx.writeAndFlush(packet)
    }

    private var lastFlush = 0L
    private fun tryFlush() {
        if (System.currentTimeMillis() - lastFlush > 50)
            ctx.flush()
        lastFlush = System.currentTimeMillis()
    }

    class SubHandler(private val address: InetSocketAddress) : MultiplexHandler() {
        override fun handlerAdded(ctx: ChannelHandlerContext) {
            super.handlerAdded(ctx)
            if (bound.containsKey(address)) error("$address Already bind UDPChannel")
            bound[address] = this
        }

        override fun writeUdp(msg: ByteBuf) {
            UDPMultiplex.ctx.write(DatagramPacket(msg, address))
        }

        override fun flush(ctx: ChannelHandlerContext?) {
            super.flush(ctx)
            tryFlush()
        }

        override fun handlerRemoved(ctx: ChannelHandlerContext) {
            super.handlerRemoved(ctx)
            bound.remove(address, this)
        }
    }
}