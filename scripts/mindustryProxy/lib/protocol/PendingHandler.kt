package mindustryProxy.lib.protocol

import io.netty.channel.Channel
import mindustryProxy.lib.Manager
import mindustryProxy.lib.Server
import mindustryProxy.lib.packet.FrameworkMessage
import mindustryProxy.lib.packet.Packet
import java.net.InetAddress
import java.net.InetSocketAddress

object PendingHandler : BossHandler.Handler {
    class Connection(private val tcp: Channel) : BossHandler.Connection {
        override val address: InetAddress = (tcp.remoteAddress() as InetSocketAddress).address
        private var udpRegistered = false
        override fun setHandler(handler: BossHandler.Handler) {
            tcp.setBossHandler(handler)
        }

        override fun sendPacket(packet: Packet, udp: Boolean) {
            if (udp && !udpRegistered) error("UDP has not registered")
            if (packet is FrameworkMessage.RegisterUDP)
                udpRegistered = true
            packet.udp = udp
            tcp.writeAndFlush(packet)
        }

        override fun isActive() = tcp.isActive
        override fun close() {
            tcp.close()
            Server.Udp.disconnect(tcp)
        }
    }

    override fun connected(channel: Channel): BossHandler.Connection {
        val con = Connection(channel)
        val id = Server.Udp.registerTcp(channel)
        con.sendPacket(FrameworkMessage.RegisterTCP(id), udp = false)
        return con
    }

    override fun handle(con: BossHandler.Connection, packet: Packet) {
        println(packet)
        if (packet.udp && packet is FrameworkMessage.RegisterUDP) {
            con.sendPacket(packet, false)
            finishConnect(con)
        }
    }

    private fun finishConnect(con: BossHandler.Connection) {
        Manager.connected(con)
    }
}