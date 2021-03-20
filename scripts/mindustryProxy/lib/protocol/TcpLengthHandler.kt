package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.codec.ByteToMessageDecoder

interface TcpLengthHandler {
    class Decoder : ByteToMessageDecoder() {
        override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
            if (`in`.readableBytes() < 2) return
            `in`.markReaderIndex()
            val length = `in`.readShort().toInt()
            if (`in`.readableBytes() < length) {
                `in`.resetReaderIndex()
                return
            }
            out.add(`in`.readSlice(length).retain())
        }
    }

    @ChannelHandler.Sharable
    object Encoder : ChannelOutboundHandlerAdapter() {
        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            if (ctx.channel() !is DatagramChannel) {
                msg as ByteBuf
                val out = ctx.alloc().buffer().retain()
                out.writeShort(msg.readableBytes())
                out.writeBytes(msg)
                ctx.write(out, promise)
                out.release()
            } else ctx.write(msg, promise)
        }
    }
}