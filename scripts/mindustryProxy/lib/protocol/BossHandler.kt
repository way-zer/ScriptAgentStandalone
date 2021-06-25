package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mindustryProxy.lib.packet.Packet

class BossHandler(var handler: Handler) : ChannelInboundHandlerAdapter() {
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

    private lateinit var con: Connection
    override fun channelActive(ctx: ChannelHandlerContext) {
        con = (ctx.channel() as CombinedChannel).wrapper
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