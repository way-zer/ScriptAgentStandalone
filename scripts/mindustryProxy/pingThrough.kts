package mindustryProxy

import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.protocol.UpStreamConnection

listenTo<PingEvent> {
    result = UpStreamConnection.ping(Manager.defaultServer) ?: result
}