package mindustryProxy.lib

import mindustryProxy.lib.event.PlayerConnectEvent
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.ConnectPacket
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.protocol.BossHandler

class ProxiedPlayer {
    private lateinit var clientCon: BossHandler.Connection
    private var server: BossHandler.Connection? = null
    lateinit var connectPacket: ConnectPacket

    fun connected(con: BossHandler.Connection) {
        clientCon = con
        clientCon.setHandler(clientHandler)
    }

    fun handle(packet: Packet, fromServer: Boolean) {
        if (!fromServer && packet is ConnectPacket) {
            connectPacket = packet
            val event = PlayerConnectEvent(this, true).also { it.emit() }
            if (event.cancelled) close()
            else Manager.connectServer(this)
            return
        }
        if (PlayerPacketEvent(this, packet, fromServer).also { it.emit() }.cancelled) return
        if (fromServer) clientCon.sendPacket(packet, packet.udp)
        else server?.sendPacket(packet, packet.udp)
    }

    fun connectedServer(con: BossHandler.Connection) {
        server.let { old ->
            server = con
            old?.close()
        }
        con.setHandler(serverHandler)
        if (con.isActive()) con.sendPacket(connectPacket, false)
        else close()
    }

    fun close() {
        server?.close()
        clientCon.close()
        Manager.disconnected(this)
    }

    private val clientHandler = object : BossHandler.Handler {
        override fun disconnected(con: BossHandler.Connection) = close()
        override fun handle(con: BossHandler.Connection, packet: Packet) = handle(packet, false)
    }

    private val serverHandler = object : BossHandler.Handler {
        override fun disconnected(con: BossHandler.Connection) {
            if (con == server) close()
        }

        override fun handle(con: BossHandler.Connection, packet: Packet) = handle(packet, true)
    }
}