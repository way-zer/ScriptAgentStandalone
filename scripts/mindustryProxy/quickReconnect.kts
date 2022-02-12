package mindustryProxy

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import mindustryProxy.lib.event.PlayerDisconnectEvent
import mindustryProxy.lib.event.PlayerServerEvent
import mindustryProxy.lib.packet.ConnectPacket
import java.net.InetSocketAddress

val channel = MutableSharedFlow<PlayerServerEvent>(0, 0)
val lastServer = mutableMapOf<String, InetSocketAddress>()

listenTo<PlayerDisconnectEvent> {
    if (player.connectPacket == ConnectPacket.NULL) return@listenTo
    val uuid = player.connectPacket.getUuidString()
    val addr = lastServer[uuid] ?: return@listenTo

    launch {
        withTimeoutOrNull(120_000) {
            channel.filter { it.player.connectPacket.getUuidString() == uuid }
                .onEach {
                    logger.info("Quick Reconnect $uuid <--> $addr")
                    it.server = addr
                }.first()
        }
    }
}

listenTo<PlayerServerEvent> {
    runBlocking {
        channel.emit(this@listenTo)
    }
    lastServer[player.connectPacket.getUuidString()] = server
}