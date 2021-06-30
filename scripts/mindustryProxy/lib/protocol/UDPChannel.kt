package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap

class UDPChannel(private val parent: DatagramChannel, val address: InetSocketAddress) : SimpleEmbeddedChannel() {
    override fun remoteAddress0(): SocketAddress = address
    override fun localAddress0(): SocketAddress = parent.localAddress()

    override fun writeToParent(list: List<ByteBuf>) {
        parent.eventLoop().execute {
            list.forEach {
                parent.write(DatagramPacket(it, address))
            }
            parent.flush()
        }
    }

    override fun doClose0() {
        unregister(this)
    }

    companion object : ChannelInboundHandlerAdapter() {
        private var parent: DatagramChannel? = null
        private val bound = ConcurrentHashMap<InetSocketAddress, UDPChannel>()
        fun register(address: InetSocketAddress): UDPChannel {
            assert(parent != null)
            if (bound.containsKey(address)) error("$address Already bind UDPChannel")
            val new = UDPChannel(parent!!, address)
//            parent!!.eventLoop().parent().register(new).sync()
            bound[address] = new
            return new
        }

        fun unregister(channel: UDPChannel) {
            channel.deregister()
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
            child.addBuffer(msg.content())
        }
    }
}