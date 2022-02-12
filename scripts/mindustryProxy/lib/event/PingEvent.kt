package mindustryProxy.lib.event

import cf.wayzer.scriptAgent.Event
import mindustryProxy.lib.packet.PingInfo
import java.net.InetSocketAddress

class PingEvent(val addr: InetSocketAddress, var result: PingInfo) : Event, Event.Cancellable {
    override var cancelled: Boolean = false
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}