package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.ProxiedPlayer

/**
 * @param info 是否ConnectPacket传输完毕
 */
class PlayerConnectEvent(val player: ProxiedPlayer, val info: Boolean) : Event, Event.Cancellable {
    override var cancelled = false
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}