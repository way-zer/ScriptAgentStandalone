package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.ProxiedPlayer

class PlayerDisconnectEvent(val player: ProxiedPlayer) : Event {
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}