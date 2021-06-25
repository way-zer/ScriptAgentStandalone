package mindustryProxy.lib.protocol

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
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
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object UpStreamConnection {
    private val tcpBoot
        get() = Bootstrap().group(Server.group)
            .channel(NioSocketChannel::class.java)
            .handler(TcpLengthHandler())
    private val udpBoot: Bootstrap
        get() = Bootstrap().group(Server.group)
            .channel(NioDatagramChannel::class.java)
            .option(ChannelOption.RCVBUF_ALLOCATOR, FixedRecvByteBufAllocator(8192))
            .handler(UDPUnpack)

    suspend fun connect(server: InetSocketAddress): Connection {
        val tcp = tcpBoot.connect(server).channel()
        val udp = udpBoot.connect(server).channel()
        return suspendCancellableCoroutine {
            val combined = CombinedChannel(tcp as SocketChannel, udp)
            it.invokeOnCancellation { combined.close() }
            udp.pipeline().addLast(combined.handler)
            tcp.pipeline().addLast(combined.handler)
            combined.setBossHandler(UpStreamHandShake(it))
        }
    }

    class UpStreamHandShake(private val finish: CancellableContinuation<Connection>) : BossHandler.Handler {
        override fun connected(con: Connection) {

        }

        override fun handle(con: Connection, packet: Packet) {
            when (packet) {
                is FrameworkMessage.RegisterTCP -> {
                    con.sendPacket(FrameworkMessage.RegisterUDP(packet.id), udp = true)
                    con.flush()
                }
                is FrameworkMessage.RegisterUDP -> {
                    if (!finish.isCompleted)
                        finish.resume(con)
                }
            }
            ReferenceCountUtil.release(packet)
        }

        override fun readComplete() {}

        override fun disconnected(con: Connection) {
            if (!finish.isCompleted)
                finish.resumeWithException(ClosedChannelException())
            con.close()
        }

        override fun onError(con: Connection, e: Throwable) {
            if (!finish.isCompleted)
                finish.resumeWithException(e)
            con.close()
        }
    }

    /**
     * @throws EOFException connection reset
     * @throws ConnectTimeoutException timeout
     */
    fun ping(server: InetSocketAddress, timeoutMillis: Long = 5000): PingInfo? {
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