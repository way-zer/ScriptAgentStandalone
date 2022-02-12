package mindustryProxy.lib

import cf.wayzer.scriptAgent.emit
import kotlinx.coroutines.launch
import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.event.PlayerConnectEvent
import mindustryProxy.lib.event.PlayerDisconnectEvent
import mindustryProxy.lib.event.PlayerServerEvent
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.protocol.Connection
import mindustryProxy.lib.protocol.UpStreamConnection
import java.net.InetSocketAddress
import java.util.logging.Level

object Manager {
    var defaultServer = InetSocketAddress("mdt.wayzer.cf", 7000)
    val players = mutableSetOf<ProxiedPlayer>()

    fun connected(con: Connection) {
        Server.logger.info("Connection from ${con.address}")
        val player = ProxiedPlayer()
        player.connected(con)
        if (con.isActive) {
            val event = PlayerConnectEvent(player, false).emit()
            if (event.cancelled) player.close()
            else players.add(player)
        } else player.close()
    }

    fun connected(player: ProxiedPlayer) {
        Server.logger.info("Player ${player.connectPacket.name} connected")
        val event = PlayerConnectEvent(player, true).emit()
        if (event.cancelled) player.close()
        else connectServer(player)
    }

    fun getPingInfo(address: InetSocketAddress): PingInfo? {
        Server.logger.info("Ping from $address")
        val default = PingInfo(
            "MindustryProxy", "None", 1, 1, 126, "proxy",
            PingInfo.GameMode.Editor, -1, "Powered by WayZer", "custom"
        )
        val event = PingEvent(address, default).emit()
        return event.result.takeIf { !event.cancelled }
    }

    fun disconnected(player: ProxiedPlayer) {
        Server.logger.info("Player ${player.connectPacket.name} disconnect")
        players.remove(player)
        PlayerDisconnectEvent(player).emit()
    }

    fun closeAll() {
        players.toList().forEach { it.close() }
    }

    fun connectServer(player: ProxiedPlayer, server: InetSocketAddress? = null) {
        val event = PlayerServerEvent(player, server ?: defaultServer).emit()
        Server.logger.info("Player ${player.connectPacket.name} <--> ${event.server}")
        Server.launch {
            try {
                val con = UpStreamConnection.connect(event.server)
                player.connectedServer(con)
                Server.logger.info("Player ${player.connectPacket.name} <==> ${event.server}")
            } catch (e: Throwable) {
                Server.logger.log(Level.WARNING, "Player ${player.connectPacket.name} <=//=> ${event.server}", e)
            }
        }
    }
}