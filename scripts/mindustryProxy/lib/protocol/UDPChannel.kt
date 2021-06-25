package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class UDPChannel(val parent: DatagramChannel, val address: InetSocketAddress) : EmbeddedChannel() {
    override fun remoteAddress0(): SocketAddress = address

    private val flushing = AtomicBoolean(false)
    override fun doWrite(`in`: ChannelOutboundBuffer) {
        if (`in`.isEmpty) return
        if (!flushing.compareAndSet(false, true)) return
        eventLoop().execute {
            flushing.set(false)
            while (true) {
                val buf = (`in`.current() ?: break) as ByteBuf
                parent.write(DatagramPacket(buf.retain(), address))
                `in`.remove()
            }
            parent.flush()
        }
    }

    override fun doClose() {
        super.doClose()
        unregister(this)
    }

    companion object : ChannelInboundHandlerAdapter() {
        private var parent: DatagramChannel? = null
        private val bound = ConcurrentHashMap<InetSocketAddress, UDPChannel>()
        fun register(address: InetSocketAddress): UDPChannel {
            assert(parent != null)
            if (bound.containsKey(address)) error("$address Already bind UDPChannel")
            val new = UDPChannel(parent!!, address)
            bound[address] = new
            return new
        }

        fun unregister(channel: UDPChannel) {
            bound.remove(channel.address, channel)
        }

        override fun channelRegistered(ctx: ChannelHandlerContext) {
            parent = ctx.channel() as DatagramChannel
        }

        /**
         * To child or pass to other handler
         */
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            msg as DatagramPacket
            val child = bound[msg.sender()] ?: return super.channelRead(ctx, msg)
            child.handleInboundMessage(msg.content())
        }
    }
}