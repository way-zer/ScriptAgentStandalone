package mindustryProxy.lib

import cf.wayzer.scriptAgent.emit
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.ConnectPacket
import mindustryProxy.lib.packet.InvokePacket
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.protocol.BossHandler

class ProxiedPlayer {
    private lateinit var clientCon: BossHandler.Connection
    private var server: BossHandler.Connection? = null
    var connectPacket: ConnectPacket = ConnectPacket.NULL

    fun connected(con: BossHandler.Connection) {
        clientCon = con
        clientCon.setHandler(clientHandler)
    }

    fun handle(packet: Packet, fromServer: Boolean) {
        if (!fromServer && packet is ConnectPacket) {
            if (connectPacket != ConnectPacket.NULL)
                error("Player ${connectPacket.name} has connected")
            connectPacket = packet
            Manager.connected(this)
            ReferenceCountUtil.release(packet)
            return
        }
        if (PlayerPacketEvent(this, packet, fromServer).emit().cancelled) {
            ReferenceCountUtil.release(packet)
            return
        }
        if (fromServer) clientCon.sendPacket(packet, packet.udp)
        else server?.sendPacket(packet, packet.udp) ?: ReferenceCountUtil.release(packet)
    }

    fun connectedServer(con: BossHandler.Connection) {
        val old = server
        server = con
        old?.close()

        con.setHandler(serverHandler)
        if (con.isActive()) {
            if (old != null)//Send WorldBegin
                clientCon.sendPacket(InvokePacket(0, 0, Unpooled.EMPTY_BUFFER), false)
            con.sendPacket(connectPacket, false)
        } else close()
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