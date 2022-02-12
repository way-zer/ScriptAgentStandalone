package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.SocketChannel
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.Packet
import java.net.InetSocketAddress
import java.util.logging.Level

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
            con.close()
        }
    }

    lateinit var handler: Handler
    val con = object : Connection {
        override val address: InetSocketAddress
            get() = channel.remoteAddress()
        override val isActive: Boolean
            get() = channel.isActive
        override val unsafe: SocketChannel
            get() = channel

        override fun sendPacket(packet: Packet, udp: Boolean) {
            packet.udp = udp
            if (!channel.isActive) {
                ReferenceCountUtil.release(packet)
                return
            }
            channel.write(packet).addListener {
                if (!it.isSuccess) {
                    Server.logger.log(Level.WARNING, "Fail to sendPacket $packet", it.cause())
                }
            }
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