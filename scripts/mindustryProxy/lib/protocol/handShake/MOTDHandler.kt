package mindustryProxy.lib.protocol.handShake

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import mindustryProxy.lib.Manager
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.packet.Registry

object MOTDHandler : SimpleChannelInboundHandler<DatagramPacket>(false) {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
        val sender = msg.sender()
        when (val packet = Registry.decodeOnlyFrameworkMessage(msg.content())) {
            is FrameworkMessage.DiscoverHost -> {
                val result = Manager.getPingInfo(sender) ?: return
                val resp = PingInfo.encode(ctx.alloc().directBuffer(), result)
                ctx.write(DatagramPacket(resp, sender))
            }
            is FrameworkMessage.RegisterUDP -> {
                HandShakeHandler.registerUDP(packet.id, msg.sender())
            }
            else -> {}
        }
    }
}