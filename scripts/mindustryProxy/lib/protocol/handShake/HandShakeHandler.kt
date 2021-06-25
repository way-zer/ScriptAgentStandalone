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
import mindustryProxy.lib.protocol.CombinedChannel
import mindustryProxy.lib.protocol.TcpLengthHandler
import mindustryProxy.lib.protocol.UDPChannel
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
        val packet = FrameworkMessage.RegisterTCP(id)
        val out = ctx.alloc().directBuffer().also {
            Registry.encode(it, packet)
        }
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
        ctx.channel().attr(connectionId).get()?.let {
            synchronized(pending) {
                pending.remove(it)
            }
        }
    }

    //For UDP
    fun registerUDP(id: Int, addr: InetSocketAddress): Boolean {
        val tcp = synchronized(pending) {
            pending.remove(id) ?: return false
        }
        val udp = UDPChannel.register(addr)
        val combined = CombinedChannel(tcp, udp)

        //Only Keep TcpLength
        while (true) {
            if (tcp.pipeline().last() is TcpLengthHandler) break
            tcp.pipeline().removeLast()
        }
        tcp.pipeline().addLast(combined.handler)
        udp.pipeline().addLast(combined.handler)

        Manager.connected(combined.wrapper)
        combined.wrapper.sendPacket(FrameworkMessage.RegisterUDP(id), false)
        combined.wrapper.flush()
        return true
    }
}