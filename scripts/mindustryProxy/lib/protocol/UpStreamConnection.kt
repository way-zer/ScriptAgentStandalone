package mindustryProxy.lib.protocol

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioSocketChannel
import mindustryProxy.lib.ProxiedPlayer
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Packet
import java.net.InetAddress
import java.net.InetSocketAddress

class UpStreamConnection(private val player: ProxiedPlayer) : BossHandler.Connection {
    override lateinit var address: InetAddress
    lateinit var tcp: Channel
    lateinit var udp: Channel

    override fun isActive() = tcp.isActive && udp.isActive
    override fun setHandler(handler: BossHandler.Handler) {
        tcp.setBossHandler(handler)
        udp.setBossHandler(handler)
    }

    override fun sendPacket(packet: Packet, udp: Boolean) {
        (if (udp) this.udp else tcp).writeAndFlush(packet)
    }

    override fun close() {
        tcp.disconnect()
        udp.disconnect()
    }

    fun connect(server: InetSocketAddress) {

        address = server.address
        val initializer = Initializer(this)
        udp = Bootstrap().group(Server.group).channel(NioDatagramChannel::class.java)
            .handler(initializer).connect(server).channel()
        tcp = Bootstrap().group(Server.group).channel(NioSocketChannel::class.java)
            .handler(initializer).connect(server).channel()
    }

    private fun finishConnect() {
        player.connectedServer(this)
    }

    class Initializer(private val upstream: UpStreamConnection) : ChannelInitializer<Channel>(), BossHandler.Handler {
        override fun initChannel(ch: Channel) {
            Server.initChannel(ch)
            ch.pipeline().get(BossHandler::class.java).handler = this
            if (ch is DatagramChannel) {
                ch.pipeline().addAfter(
                    Server.Handler.TcpLengthDecoder.name, "UDPUnpack",
                    object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                            ctx.fireChannelRead((msg as DatagramPacket).content())
                        }
                    })
            }
        }

        override fun connected(channel: Channel): BossHandler.Connection {
            return upstream
        }

        override fun handle(con: BossHandler.Connection, packet: Packet) {
            Server.logger.info("Upstream: $packet")
            when (packet) {
                is FrameworkMessage.RegisterTCP -> {
                    upstream.sendPacket(FrameworkMessage.RegisterUDP(packet.id), udp = true)
                }
                is FrameworkMessage.RegisterUDP -> {
                    upstream.finishConnect()
                }
            }
        }

        override fun disconnected(con: BossHandler.Connection) {
            con.close()
        }
    }
}