package mindustryProxy.lib.protocol

import io.netty.channel.ChannelHandler
import io.netty.channel.CombinedChannelDuplexHandler
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender

class TcpLengthHandler :
    CombinedChannelDuplexHandler<TcpLengthHandler.Decoder, TcpLengthHandler.Encoder>(Decoder(), Encoder) {

    class Decoder : LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, Length, 0, Length)

    @ChannelHandler.Sharable
    object Encoder : LengthFieldPrepender(Length)
    companion object {
        const val Length = 2 //short
    }
}