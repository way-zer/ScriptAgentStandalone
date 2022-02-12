package mindustryProxy.lib.protocol

import io.netty.channel.socket.SocketChannel
import mindustryProxy.lib.packet.Packet
import java.net.InetSocketAddress

interface Connection {
    val address: InetSocketAddress
    val isActive: Boolean
    val unsafe: SocketChannel

    fun sendPacket(packet: Packet, udp: Boolean)
    fun setBossHandler(handler: BossHandler.Handler)
    fun flush()
    fun close()
}