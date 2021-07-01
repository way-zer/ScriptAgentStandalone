package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.SocketChannel
import mindustryProxy.lib.packet.Packet
import java.net.InetAddress

class BossHandler(val channel: SocketChannel) : ChannelInboundHandlerAdapter() {
    class FailHandlePacket(packet: Packet, cause: Throwable) : Exception("Fail to handle $packet", cause)
    interface Handler {
        //only call once, useless for handler after init
        fun connected(con: Connection) = Unit
        fun disconnected(con: Connection) = Unit
        fun handle(con: Connection, packet: Packet)

        //time to flush
        fun readComplete()
        fun onError(con: Connection, e: Throwable) {
            e.printStackTrace()
        }
    }

    lateinit var handler: Handler
    val con = object : Connection {
        override val address: InetAddress
            get() = channel.remoteAddress().address
        override val isActive: Boolean
            get() = channel.isActive
        override val unsafe: SocketChannel
            get() = channel

        override fun sendPacket(packet: Packet, udp: Boolean) {
            packet.udp = udp
            channel.write(packet)
        }

        override fun setBossHandler(handler: Handler) {
            this@BossHandler.handler = handler
        }

        override fun flush() {
            channel.flush()
        }

        override fun close() {
            if (channel.isActive)
                channel.close()
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        assert(ctx.channel() == channel)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        handler.connected(con)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        checkRef(msg)
        msg as Packet
        try {
            handler.handle(con, msg)
        } catch (e: Exception) {
            throw FailHandlePacket(msg, e)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        handler.readComplete()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        handler.disconnected(con)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        handler.onError(con, cause)
    }
}