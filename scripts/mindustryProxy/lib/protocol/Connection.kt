package mindustryProxy.lib.protocol

import mindustryProxy.lib.packet.Packet
import java.net.InetAddress

interface Connection {
    val address: InetAddress
    val isActive: Boolean
    val unsafe: CombinedChannel

    fun sendPacket(packet: Packet, udp: Boolean)
    fun setBossHandler(handler: BossHandler.Handler)
    fun flush()
    fun close()
}