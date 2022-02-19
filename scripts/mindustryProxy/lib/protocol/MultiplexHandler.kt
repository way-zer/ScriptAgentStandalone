package mindustryProxy.lib.protocol

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.Registry
import java.net.InetSocketAddress
import kotlin.coroutines.resume

abstract class MultiplexHandler : ChannelDuplexHandler() {
    protected lateinit var ctx: ChannelHandlerContext
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.ctx = ctx
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val packet = Registry.decode(msg as ByteBuf)
        ctx.fireChannelRead(packet)
    }

    override fun write(ctx: ChannelHandlerContext, packet: Any, promise: ChannelPromise) {
        val out = Registry.encode(ctx.alloc().directBuffer(), packet as Packet)
        if (packet.udp) {
            try {
                writeUdp(out)
                promise.setSuccess()
            } catch (e: Throwable) {
                promise.setFailure(e)
            }
        } else {
            if (packet is FrameworkMessage)
                ctx.writeAndFlush(out, promise)
            else
                ctx.write(out, promise)
        }
    }

    abstract fun writeUdp(msg: ByteBuf)
    fun onUdpRead(msg: ByteBuf) {
        val packet = Registry.decode(msg)
        packet.udp = true
        ctx.fireChannelRead(packet)
    }

    fun onUdpReadComplete() {
        ctx.fireChannelReadComplete()
    }

    protected class WarpSingleChannel(val udp: DatagramChannel) : MultiplexHandler() {
        val udpHandler = object : ChannelInboundHandlerAdapter() {
            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                onUdpRead(msg as ByteBuf)
            }

            override fun channelReadComplete(ctx: ChannelHandlerContext?) {
                super.channelReadComplete(ctx)
                onUdpReadComplete()
            }
        }

        override fun writeUdp(msg: ByteBuf) {
            udp.write(msg)
        }

        override fun flush(ctx: ChannelHandlerContext) {
            udp.flush()
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            udp.close()
        }
    }

    companion object {
        suspend fun wrapConnect(boot: Bootstrap, addr: InetSocketAddress)
                : MultiplexHandler = suspendCancellableCoroutine { co ->
            val udp = boot.handler(object : ChannelInitializer<DatagramChannel>() {
                override fun initChannel(ch: DatagramChannel) {
                    ch.pipeline().addLast(UDPUnpack)
                    val handler = WarpSingleChannel(ch)
                    ch.pipeline().addLast(handler.udpHandler)
                    co.resume(handler)
                }
            }).connect(addr).channel()
            co.invokeOnCancellation { udp.close() }
        }
    }
}