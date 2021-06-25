package mindustryProxy.lib

import cf.wayzer.scriptAgent.define.ScriptInfo
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.FixedRecvByteBufAllocator
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import mindustryProxy.lib.protocol.TcpLengthHandler
import mindustryProxy.lib.protocol.UDPChannel
import mindustryProxy.lib.protocol.handShake.HandShakeHandler
import mindustryProxy.lib.protocol.handShake.MOTDHandler
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

object Server : CoroutineScope {
    var logger: Logger = Logger.getLogger("Server") //change to script logger if in script
    override val coroutineContext: CoroutineContext
        get() = group.asCoroutineDispatcher()

    internal var group = NioEventLoopGroup(16)
    private val onClose = mutableSetOf<() -> Unit>()
    fun start(port: Int) {
        if (group.isTerminated)
            group = NioEventLoopGroup(16)
        Bootstrap().group(group).channel(NioDatagramChannel::class.java)
            .option(ChannelOption.RCVBUF_ALLOCATOR, FixedRecvByteBufAllocator(8192))
            .handler(object : ChannelInitializer<DatagramChannel>() {
                override fun initChannel(ch: DatagramChannel) {
                    ch.pipeline().apply {
                        addLast(UDPChannel)
                        addLast(MOTDHandler)
//                        addLast(ShakeHand) //in MOTDHandler
                    }
                }
            }).bind(port)
            .also { onClose += { it.channel().close() } }
        ServerBootstrap().group(group).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        addLast(TcpLengthHandler())
                        addLast(ReadTimeoutHandler(3))
                        addLast(HandShakeHandler)
                    }
                }
            }).bind(port)
            .also { onClose += { it.channel().close() } }
        logger.info("Host on $port")
    }

    fun stop() {
        logger.info("Going to stop proxy")
        onClose.forEach { it.invoke() }
        onClose.clear()
        group.awaitTermination(5000, TimeUnit.MILLISECONDS)
//        group.shutdownGracefully(1000, 5000, TimeUnit.MILLISECONDS).sync()
        logger.info("Goodbye!")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Class.forName("mindustryProxy.ReconnectSupport").getConstructor(ScriptInfo::class.java)
            .newInstance(ScriptInfo.get("mindustryProxy/reconnectProxy"))
        start(6567)
        Thread.sleep(99999999)
    }
}