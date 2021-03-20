package mindustryProxy.lib.protocol

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mindustryProxy.lib.packet.Packet
import java.net.InetAddress

class BossHandler : ChannelInboundHandlerAdapter() {
    interface Connection {
        val address: InetAddress
        fun setHandler(handler: Handler)
        fun isActive(): Boolean
        fun sendPacket(packet: Packet, udp: Boolean)
        fun close()
        fun Channel.setBossHandler(handler: Handler) {
            pipeline().get(BossHandler::class.java).handler = handler
        }
    }

    interface Handler {
        //only call once, useless for handler after init
        fun connected(channel: Channel): Connection = throw UnsupportedOperationException()
        fun disconnected(con: Connection) = Unit
        fun handle(con: Connection, packet: Packet)
        fun onError(con: Connection, e: Throwable) {
            e.printStackTrace()
        }
    }

    var handler: Handler = PendingHandler
    private lateinit var con: Connection

    override fun channelActive(ctx: ChannelHandlerContext) {
        con = handler.connected(ctx.channel())
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        handler.handle(con, msg as Packet)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        handler.disconnected(con)
        con.close()
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        handler.onError(con, cause)
    }
}