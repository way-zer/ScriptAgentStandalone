package mindustryProxy.lib

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.collection.IntObjectHashMap
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.PingInfo
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object UDPServer : ChannelInboundHandlerAdapter() {
    object Registry {
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

        fun unregisterTcp(tcp: Channel) {
            tcp.attr(connectionId).get()?.let(pending::remove)
            tcp.attr(udpAddr).get()?.let(tcpMap::remove)
        }

        fun registerUDP(id: Int, address: InetSocketAddress): Boolean {
            val tcp = pending[id] ?: return false
            tcp.attr(udpAddr).set(address)
            tcpMap[address] = tcp
            return true
        }
    }

    private val udpAddr: AttributeKey<InetSocketAddress> = AttributeKey.newInstance("udpAddr")
    private val tcpMap = ConcurrentHashMap<InetSocketAddress, Channel>()

    fun send(addr: InetSocketAddress, packet: ByteBuf) {
        inst.writeAndFlush(DatagramPacket(packet, addr))
    }

    fun send(tcp: Channel, packet: ByteBuf) {
        send(tcp.attr(udpAddr).get()!!, packet)
    }

    fun onReceive(sender: InetSocketAddress, packet: Packet) {
        when (packet) {
            is FrameworkMessage.DiscoverHost -> {
                val out = inst.alloc().buffer()
                PingInfo.encode(out, Manager.getPingInfo(sender.address))
                send(sender, out)
            }
            is FrameworkMessage.RegisterUDP -> {
                if (Registry.registerUDP(packet.id, sender)) {
                    packet.udp = true
                    tcpMap[sender]?.pipeline()?.fireChannelRead(packet)
                }
            }
            else -> {
                packet.udp = true
                tcpMap[sender]?.pipeline()?.fireChannelRead(packet)
                    ?: ReferenceCountUtil.release(packet)
            }
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as DatagramPacket
        ctx.channel().eventLoop().parent().execute {
            val sender = msg.sender()
            val packet = mindustryProxy.lib.packet.Registry.decode(msg.content())
            msg.release()
            onReceive(sender, packet)
        }
    }

    private lateinit var inst: DatagramChannel
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        inst = ctx.channel() as DatagramChannel
    }

    fun start(group: EventLoopGroup): Bootstrap {
        return Bootstrap().group(group).channel(NioDatagramChannel::class.java)
            .option(ChannelOption.RCVBUF_ALLOCATOR, FixedRecvByteBufAllocator(8192))
            .handler(this)
    }
}