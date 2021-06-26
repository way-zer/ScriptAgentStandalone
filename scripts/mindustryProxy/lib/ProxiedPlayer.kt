package mindustryProxy.lib

import cf.wayzer.scriptAgent.emit
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.ConnectPacket
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.UnknownPacket
import mindustryProxy.lib.protocol.BossHandler
import mindustryProxy.lib.protocol.Connection

class ProxiedPlayer {
    lateinit var clientCon: Connection
        private set
    var server: Connection? = null
        private set
    var connectPacket: ConnectPacket = ConnectPacket.NULL

    fun connected(con: Connection) {
        clientCon = con
        clientCon.setBossHandler(clientHandler)
    }

    fun handle(packet: Packet, fromServer: Boolean) {
        if (!fromServer && packet is ConnectPacket) {
            if (connectPacket != ConnectPacket.NULL)
                error("Player ${connectPacket.name} has connected")
            connectPacket = packet
            Manager.connected(this)
            return
        }
        if (PlayerPacketEvent(this, packet, fromServer).emit().cancelled) {
            ReferenceCountUtil.release(packet)
            return
        }
        if (fromServer) clientCon.sendPacket(packet, packet.udp)
        else server?.sendPacket(packet, packet.udp) ?: ReferenceCountUtil.release(packet)
    }

    fun connectedServer(con: Connection) {
        val old = server
        server = con
        old?.close()

        con.setBossHandler(serverHandler)
        if (con.isActive) {
            if (old != null)//Send WorldBegin
                clientCon.sendPacket(UnknownPacket(88, Unpooled.EMPTY_BUFFER), false)
//                clientCon.sendPacket(InvokePacket(0, 0, Unpooled.EMPTY_BUFFER), false)
            con.sendPacket(connectPacket, false)
            con.flush()
        } else close()
    }

    @Volatile
    var closed = false
    fun close() {
        if (closed) return
        closed = true
        server?.close()
        clientCon.close()
        Manager.disconnected(this)
    }

    private val clientHandler = object : BossHandler.Handler {
        override fun disconnected(con: Connection) = close()
        override fun handle(con: Connection, packet: Packet) = handle(packet, false)
        override fun readComplete() {
            server?.flush()
        }
    }

    private val serverHandler = object : BossHandler.Handler {
        override fun disconnected(con: Connection) {
            if (con == server) close()
        }

        override fun handle(con: Connection, packet: Packet) = handle(packet, true)
        override fun readComplete() {
            clientCon.flush()
        }
    }
}