package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.SocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.Registry
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * After TCP/UDP both handShake
 * 合并TCP，UDP为一个Channel
 * 同时实现PacketHandler功能
 * Message 为 [Packet]
 * Handler链为 [StreamableHandler] [BossHandler]
 */
class CombinedChannel(private val tcp: SocketChannel, private val udp: Channel) : EmbeddedChannel(
    false, true
) {
    override fun remoteAddress(): InetSocketAddress = tcp.remoteAddress()

    override fun doClose() {
        if (isActive)
            super.doClose()
        if (tcp.isActive)
            tcp.close()
        if (udp.isActive)
            udp.close()
    }

    override fun doDisconnect() {
        tcp.disconnect()
        udp.disconnect()
    }

    fun setBossHandler(handler: BossHandler.Handler) {
        val last = pipeline().last()
        if (last is BossHandler) {
            last.handler = handler
            return
        }
        initPipeline(BossHandler(handler))
    }

    private fun initPipeline(boss: BossHandler) {
        pipeline().addLast(object : ChannelOutboundHandlerAdapter() {
            override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise?) {
                val packet = msg as Packet
                val out = alloc().directBuffer().also {
                    Registry.encode(it, packet)
                }
                ReferenceCountUtil.release(msg)
                checkRef(out)
                if (packet.udp)
                    udp.write(out)
                else
                    tcp.write(out)
            }

            override fun flush(ctx: ChannelHandlerContext) {
                tcp.flush()
                udp.flush()
            }
        })
        pipeline().addLast(object : ReadTimeoutHandler(30) {
            override fun readTimedOut(ctx: ChannelHandlerContext) {
                if (this@CombinedChannel.isActive)
                    this@CombinedChannel.close()
            }
        })
        pipeline().addLast(StreamableHandler())
        pipeline().addLast(boss)
        if (this.isActive)
            pipeline().fireChannelActive()
    }

    @ChannelHandler.Sharable
    inner class CombinedHandler : ChannelDuplexHandler() {
        override fun handlerAdded(ctx: ChannelHandlerContext) {
            if (tcp.isActive && !this@CombinedChannel.isRegistered)
                this@CombinedChannel.register()
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            val packet = Registry.decode(msg as ByteBuf)
            msg.release()
            packet.udp = when (ctx.channel()) {
                tcp -> false
                udp -> true
                else -> {
                    ReferenceCountUtil.release(packet)
                    error("CombinedHandler Can only read from tcp or udp")
                }
            }
            this@CombinedChannel.pipeline().fireChannelRead(packet)
        }

        override fun channelReadComplete(ctx: ChannelHandlerContext) {
            this@CombinedChannel.pipeline().fireChannelReadComplete()
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            super.channelActive(ctx)
            if (ctx.channel() == tcp)
                this@CombinedChannel.register()
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            super.channelInactive(ctx)
            if (ctx.channel() == tcp)
                this@CombinedChannel.close()
        }
    }

    val handler by lazy { CombinedHandler() }

    val wrapper = object : Connection {
        override val address: InetAddress
            get() = remoteAddress().address
        override val isActive: Boolean
            get() = this@CombinedChannel.isActive
        override val unsafe: CombinedChannel
            get() = this@CombinedChannel

        override fun sendPacket(packet: Packet, udp: Boolean) {
            packet.udp = udp
            checkRef(packet)
            this@CombinedChannel.write(packet)
        }

        override fun flush() {
            this@CombinedChannel.flush()
        }

        override fun close() {
            this@CombinedChannel.disconnect()
            this@CombinedChannel.close()
        }

        override fun setBossHandler(handler: BossHandler.Handler) {
            this@CombinedChannel.setBossHandler(handler)
        }
    }
}
