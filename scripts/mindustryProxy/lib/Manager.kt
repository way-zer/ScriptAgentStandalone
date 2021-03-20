package mindustryProxy.lib

import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.event.PlayerConnectEvent
import mindustryProxy.lib.event.PlayerDisconnectEvent
import mindustryProxy.lib.event.PlayerServerEvent
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.protocol.BossHandler
import mindustryProxy.lib.protocol.UpStreamConnection
import java.net.InetAddress
import java.net.InetSocketAddress

object Manager {
    var defaultServer = InetSocketAddress("mdt.wayzer.cf", 7000)
    val players = mutableSetOf<ProxiedPlayer>()
    fun connected(con: BossHandler.Connection) {
        val player = ProxiedPlayer()
        player.connected(con)
        if (con.isActive()) {
            val event = PlayerConnectEvent(player, false).also { it.emit() }
            if (event.cancelled) player.close()
            else players.add(player)
        }
    }

    fun getPingInfo(address: InetAddress): PingInfo {
        val default = PingInfo(
            "MindustryProxy", "None", 1, 1, 126, "proxy",
            PingInfo.GameMode.Editor, -1, "Powered by WayZer", "custom"
        )
        val event = PingEvent(address, default).also { it.emit() }
        return event.result
    }

    fun disconnected(player: ProxiedPlayer) {
        players.remove(player)
        PlayerDisconnectEvent(player).emit()
    }

    fun connectServer(player: ProxiedPlayer, server: InetSocketAddress? = null) {
        val event = PlayerServerEvent(player, server ?: defaultServer).also { it.emit() }
        UpStreamConnection(player).connect(event.server)
    }
}