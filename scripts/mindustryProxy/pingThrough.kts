package mindustryProxy

import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.protocol.UpStreamConnection

listenTo<PingEvent> {
    result = UpStreamConnection.Ping(Manager.defaultServer) ?: result
}