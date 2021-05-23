package mindustryProxy.lib.protocol

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.packet.Registry
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UpStreamConnection : BossHandler.Connection {
    override lateinit var address: InetAddress
    private lateinit var tcp: Channel
    private lateinit var udp: Channel

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

    private val tcpBoot = Bootstrap().group(Server.group)
        .channel(NioSocketChannel::class.java)
        .handler(Initializer(false, this))
    private val udpBoot: Bootstrap = Bootstrap().group(Server.group)
        .channel(NioDatagramChannel::class.java)
        .option(ChannelOption.RCVBUF_ALLOCATOR, FixedRecvByteBufAllocator(8192))
        .handler(Initializer(true, this))
    private var continuation: CancellableContinuation<Unit>? = null

    suspend fun connect(server: InetSocketAddress): UpStreamConnection {
        address = server.address
        udp = udpBoot.connect(server).channel()
        tcp = tcpBoot.connect(server).channel()
        suspendCancellableCoroutine<Unit> { co ->
            co.invokeOnCancellation { close() }
            continuation = co
        }
        continuation = null
        return this
    }

    class Initializer(private val udp: Boolean, private val upstream: UpStreamConnection) :
        ChannelInitializer<Channel>(), BossHandler.Handler {
        override fun initChannel(ch: Channel) {
            Server.initChannel(ch)
            ch.pipeline().get(BossHandler::class.java).handler = this
            if (udp) {
                ch.pipeline().addAfter(Server.Handler.TcpLengthDecoder.name, "UDPUnpack", UDPUnpack)
            }
        }

        override fun connected(channel: Channel): BossHandler.Connection {
            return upstream
        }

        override fun handle(con: BossHandler.Connection, packet: Packet) {
            when (packet) {
                is FrameworkMessage.RegisterTCP -> {
                    upstream.sendPacket(FrameworkMessage.RegisterUDP(packet.id), udp = true)
                }
                is FrameworkMessage.RegisterUDP -> {
                    if (upstream.continuation?.isCompleted == false)
                        upstream.continuation?.resume(Unit)
                }
            }
            ReferenceCountUtil.release(packet)
        }

        override fun disconnected(con: BossHandler.Connection) {
            if (upstream.continuation?.isCompleted == false)
                upstream.continuation?.resumeWithException(ClosedChannelException())
            con.close()
        }

        override fun onError(con: BossHandler.Connection, e: Throwable) {
            if (upstream.continuation?.isCompleted == false)
                upstream.continuation?.resumeWithException(e)
            con.close()
        }
    }

    object Ping {
        /**
         * @throws EOFException connection reset
         * @throws ConnectTimeoutException timeout
         */
        operator fun invoke(server: InetSocketAddress, timeoutMillis: Long = 5000): PingInfo? {
            var result: PingInfo? = null
            var cause: Throwable? = null
            Bootstrap().group(Server.group)
                .channel(NioDatagramChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        ch.pipeline().addLast(UDPUnpack)
                        ch.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                val out = ctx.alloc().buffer()
                                Registry.encode(out, FrameworkMessage.DiscoverHost)
                                ctx.writeAndFlush(out)
                            }

                            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                msg as ByteBuf
                                result = PingInfo.decode(msg)
                                msg.release()
                                ctx.close()
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                if (cause == null)
                                    cause = EOFException()
                                ctx.close()
                            }
                        })
                    }
                }).connect(server).also {
                    if (!it.channel().closeFuture().await(timeoutMillis))
                        cause = ConnectTimeoutException()
                }
            if (result == null && cause != null)
                throw cause!!
            return result
        }
    }
}