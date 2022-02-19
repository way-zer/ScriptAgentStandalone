package mindustryProxy.lib

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import mindustryProxy.lib.protocol.*
import mindustryProxy.lib.protocol.handShake.HandShakeHandler
import mindustryProxy.lib.protocol.handShake.MOTDHandler
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

object Server : CoroutineScope {
    var logger: Logger = Logger.getLogger("Server") //change to script logger if in script
    override val coroutineContext: CoroutineContext
        get() = group.asCoroutineDispatcher() + SupervisorJob()

    private val onClose = mutableSetOf<() -> Unit>()
    private var group0: EventLoopGroup? = null
    val group: EventLoopGroup
        get() {
            if (group0?.isTerminated != false) {
                group0 = if (Epoll.isAvailable()) EpollEventLoopGroup() else NioEventLoopGroup()
            }
            return group0!!
        }

    fun start(port: Int) {
        val epoll = Epoll.isAvailable()
        logger.info("Epoll Support: $epoll")
        Bootstrap().group(group)
            .channel(if (epoll) EpollDatagramChannel::class.java else NioDatagramChannel::class.java)
            .handler(object : ChannelInitializer<DatagramChannel>() {
                override fun initChannel(ch: DatagramChannel) {
                    ch.pipeline().apply {
                        addLast(UDPMultiplex)
                        addLast(MOTDHandler)
//                        addLast(ShakeHand) //in MOTDHandler
                    }
                }
            }).bind(port)
            .also { onClose += { it.channel().close() } }
//        if (epoll)
//            udp.option(EpollChannelOption.SO_REUSEPORT, true)
//        repeat(if (epoll) 4 else 1) {\
//        }

        ServerBootstrap().group(group)
            .channel(if (epoll) EpollServerSocketChannel::class.java else NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        addLast(TcpLengthHandler())
                        addLast("HandTimeOut", ReadTimeoutHandler(3))
                        addLast(HandShakeHandler)
                    }
                }
            }).bind(port)
            .also { onClose += { it.channel().close() } }
        logger.info("Host on $port")
    }

    //完成MDT TCP+UDP握手
    fun afterHandshake(channel: SocketChannel, multiplex: MultiplexHandler, isUpstream: Boolean): Connection {
        val bossHandler = BossHandler(channel)
        channel.pipeline().apply {
            if (!isUpstream) {
                remove("HandTimeOut")
                remove(HandShakeHandler)
            }

            addLast(multiplex)
            addLast(object : ReadTimeoutHandler(30) {
                override fun readTimedOut(ctx: ChannelHandlerContext) {
                    if (channel.isActive)
                        channel.close()
                }
            })
            addLast(StreamableHandler())
            addLast(bossHandler)
        }
        return bossHandler.con
    }

    fun stop() {
        logger.info("Going to stop proxy")
        onClose.forEach { it.invoke() }
        onClose.clear()
        HandShakeHandler.closeAll()
        Manager.closeAll()
        group.awaitTermination(5000, TimeUnit.MILLISECONDS)
//        group.shutdownGracefully(1000, 5000, TimeUnit.MILLISECONDS).sync()
        logger.info("Goodbye!")
    }

//    @JvmStatic
//    fun main(args: Array<String>) {
//        Class.forName("mindustryProxy.ReconnectSupport").getConstructor(ScriptInfo::class.java)
//            .newInstance(ScriptInfo.get("mindustryProxy/reconnectProxy"))
//        start(6567)
//        Thread.sleep(99999999)
//    }
}