package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.ProxiedPlayer
import mindustryProxy.lib.packet.Packet

/**
 * @param fromServer 是否来自服务器,用于判断发送方向
 */
class PlayerPacketEvent(val player: ProxiedPlayer, val packet: Packet, val fromServer: Boolean) :
    Event, Event.Cancellable {
    override var cancelled = false
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}