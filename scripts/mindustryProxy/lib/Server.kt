package mindustryProxy.lib

import cf.wayzer.scriptAgent.define.ScriptInfo
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.ResourceLeakDetector
import mindustryProxy.lib.protocol.BossHandler
import mindustryProxy.lib.protocol.PacketHandler
import mindustryProxy.lib.protocol.StreamableHandler
import mindustryProxy.lib.protocol.TcpLengthHandler
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object Server : ChannelInitializer<Channel>() {
    var logger: Logger = Logger.getLogger("Server") //change to script logger if in script

    internal var group = NioEventLoopGroup(16)
    private val onClose = mutableSetOf<() -> Unit>()
    fun start(port: Int) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
        if (group.isTerminated)
            group = NioEventLoopGroup(16)
        UDPServer.start(group).bind(port)
            .also { onClose += { it.channel().close() } }
        ServerBootstrap().group(group).channel(NioServerSocketChannel::class.java)
            .childHandler(Server)
            .bind(port)
            .also { onClose += { it.channel().close() } }
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

    //Just For Name
    enum class Handler {
        Timeout, TcpLengthDecoder, TcpLengthEncoder, PacketHandler, StreamableHandler, BossHandler
    }
    //For TCP: Timeout->TcpLength->PacketHandler->StreamableHandler->BossHandler
    //For UDP: UDPUnpack->PacketHandler->TCP
}