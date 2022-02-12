package mindustryProxy.lib.protocol.handShake

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.SocketChannel
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.collection.IntObjectHashMap
import mindustryProxy.lib.Manager
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Registry
import mindustryProxy.lib.protocol.UDPMultiplex
import java.net.InetSocketAddress
import java.util.logging.Level
import kotlin.random.Random

@ChannelHandler.Sharable
object HandShakeHandler : ChannelInboundHandlerAdapter() {
    private val connectionId = AttributeKey.newInstance<Int>("connectionId")
    private val pending = IntObjectHashMap<SocketChannel>()

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (ctx.channel().attr(connectionId).get() != null) error("Already Register")
        var id: Int
        synchronized(pending) {
            do {
                id = Random.nextInt()
            } while (id in pending)
            pending[id] = ctx.channel() as SocketChannel
        }
        ctx.channel().attr(connectionId).set(id)
        val out = Registry.encode(ctx.alloc().directBuffer(), FrameworkMessage.RegisterTCP(id))
        ctx.writeAndFlush(out)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ReferenceCountUtil.release(msg)
        //Nothing need to read
        ctx.disconnect()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause !is ReadTimeoutException)
            Server.logger.log(Level.WARNING, "Channel ${ctx.channel().remoteAddress()}", cause)
        ctx.disconnect()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        ctx.channel().attr(connectionId).get()
            ?.let { pending.remove(it) }
    }

    fun closeAll() {
        pending.values.forEach { it.close() }
    }

    //For UDP
    fun registerUDP(id: Int, addr: InetSocketAddress): Boolean {
        val tcp = pending.remove(id) ?: return false
        val multiplex = UDPMultiplex.SubHandler(addr)
        val con = Server.afterHandshake(tcp, multiplex, isUpstream = false)
        Manager.connected(con)
        con.sendPacket(FrameworkMessage.RegisterUDP(id), false)
        con.flush()
        return true
    }
}