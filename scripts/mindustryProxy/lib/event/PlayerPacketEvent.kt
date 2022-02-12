package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.ProxiedPlayer
import mindustryProxy.lib.packet.Packet

/**
 * @param fromServer 是否来自服务器,用于判断发送方向
 */
class PlayerPacketEvent(val player: ProxiedPlayer, packet: Packet, fromServer: Boolean) :
    Event, Event.Cancellable {
    var packet: Packet = packet
        private set
    var fromServer = fromServer
        private set
    override var cancelled = false
    override val handler: Event.Handler get() = Companion

    fun reuse(packet: Packet, fromServer: Boolean) {
        this.cancelled = false
        this.packet = packet
        this.fromServer = fromServer
    }

    companion object : Event.Handler()
}