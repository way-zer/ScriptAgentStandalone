package mindustryProxy.lib.protocol.handShake

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import kotlinx.coroutines.launch
import mindustryProxy.lib.Manager
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.packet.Registry

object MOTDHandler : SimpleChannelInboundHandler<DatagramPacket>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
        val sender = msg.sender()
        when (val packet = Registry.decodeOnlyFrameworkMessage(msg.content())) {
            is FrameworkMessage.DiscoverHost -> {
                Server.launch {
                    val resp = ctx.alloc().directBuffer()
                    PingInfo.encode(resp, Manager.getPingInfo(sender.address))
                    ctx.writeAndFlush(DatagramPacket(resp, sender))
                }
            }
            is FrameworkMessage.RegisterUDP -> {
                HandShakeHandler.registerUDP(packet.id, msg.sender())
            }
        }
    }
}