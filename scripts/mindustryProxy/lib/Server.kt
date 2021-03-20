package mindustryProxy.lib

import cf.wayzer.scriptAgent.define.ScriptInfo
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.AttributeKey
import io.netty.util.collection.IntObjectHashMap
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Registry
import mindustryProxy.lib.protocol.BossHandler
import mindustryProxy.lib.protocol.PacketHandler
import mindustryProxy.lib.protocol.StreamableHandler
import mindustryProxy.lib.protocol.TcpLengthHandler
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.random.Random

object Server : ChannelInitializer<Channel>() {
    var logger: Logger = Logger.getLogger("Server") //change to script logger if in script

    internal val group = NioEventLoopGroup(16)
    fun start(port: Int) {
        Bootstrap().group(group).channel(NioDatagramChannel::class.java)
            .handler(Udp)
            .bind(port)
        ServerBootstrap().group(group).channel(NioServerSocketChannel::class.java)
            .childHandler(Server)
            .bind(port)
        logger.info("Host on $port")
    }

    public override fun initChannel(ch: Channel) {
        ch.pipeline().apply {
            addLast(Handler.Timeout.name, ReadTimeoutHandler(30))
            addLast(Handler.TcpLengthDecoder.name, TcpLengthHandler.Decoder())
            addLast(Handler.TcpLengthEncoder.name, TcpLengthHandler.Encoder)
            addLast(Handler.PacketHandler.name, PacketHandler)
            addLast(Handler.StreamableHandler.name, StreamableHandler())
            addLast(Handler.BossHandler.name, BossHandler())
        }
    }

    fun stop() {
        logger.info("Going to stop proxy")
        group.shutdownGracefully().sync()
        logger.info("Goodbye!")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    object Udp : ChannelInboundHandlerAdapter() {
        lateinit var inst: DatagramChannel
        override fun handlerAdded(ctx: ChannelHandlerContext) {
            inst = ctx.channel() as DatagramChannel
        }

        private val connectionId = AttributeKey.newInstance<Int>("connectionId")
        private val pending = Collections.synchronizedMap(IntObjectHashMap<Channel>())
        fun registerTcp(tcp: Channel): Int {
            if (tcp.attr(connectionId).get() != null) error("Already Register")
            var id: Int
            do {
                id = Random.nextInt()
            } while (id in pending)
            pending[id] = tcp
            tcp.attr(connectionId).set(id)
            return id
        }

        fun disconnect(tcp: Channel) {
            tcp.attr(connectionId).get()?.let(pending::remove)
            tcp.attr(udpAddr).get()?.let(addresses::remove)
        }

        private val udpAddr = AttributeKey.newInstance<InetSocketAddress>("udpAddr")
        private val addresses = ConcurrentHashMap<InetSocketAddress, Channel>()

        fun send(addr: InetSocketAddress, packet: ByteBuf) {
            inst.writeAndFlush(DatagramPacket(packet, addr))
        }

        fun send(tcp: Channel, packet: ByteBuf) {
            send(tcp.attr(udpAddr).get()!!, packet)
        }

        fun onReceive(packet: DatagramPacket) {
            when (val pack = Registry.decode(packet.content())) {
                is FrameworkMessage.DiscoverHost -> {
                    val out = inst.alloc().buffer().retain()
                    Registry.encode(out, Manager.getPingInfo(packet.sender().address))
                    send(packet.sender(), out)
                    out.release()
                }
                is FrameworkMessage.RegisterUDP -> {
                    val tcp = pending[pack.id] ?: return
                    tcp.attr(udpAddr).set(packet.sender())
                    addresses[packet.sender()] = tcp
                    pack.udp = true
                    addresses[packet.sender()]?.pipeline()?.fireChannelRead(pack)
                }
                else -> {
                    pack.udp = true
                    addresses[packet.sender()]?.pipeline()?.fireChannelRead(pack)
                }
            }
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            onReceive(msg as DatagramPacket)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        mindustryProxy.ReconnectSupport(ScriptInfo.get("mindustryProxy/reconnectProxy"))
        start(6567)
        Thread.sleep(99999999)
    }

    //Just For Name
    enum class Handler {
        Timeout, TcpLengthDecoder, TcpLengthEncoder, PacketHandler, StreamableHandler, BossHandler
    }
}