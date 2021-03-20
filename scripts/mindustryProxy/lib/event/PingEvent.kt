package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.packet.PingInfo
import java.net.InetAddress

class PingEvent(val addr: InetAddress, var result: PingInfo) : Event {
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}