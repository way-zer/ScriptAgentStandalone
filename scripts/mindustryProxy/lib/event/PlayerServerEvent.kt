package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.ProxiedPlayer
import java.net.InetSocketAddress

class PlayerServerEvent(val player: ProxiedPlayer, var server: InetSocketAddress) : Event {
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}