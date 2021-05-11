package mindustryProxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import mindustryProxy.lib.Manager
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.InvokePacket
import java.io.DataInputStream
import java.net.InetSocketAddress

listenTo<PlayerPacketEvent> {
    val packet = packet
    if (packet is InvokePacket && packet.type == 71) {
        cancelled = true
        fun ByteBuf.readStr(): String {
            return if (readBoolean()) {
                DataInputStream(ByteBufInputStream(this)).readUTF()
            } else ""
        }
        packet.data.markReaderIndex()
        val ip = packet.data.readStr()
        val port = packet.data.readInt()
        packet.data.resetReaderIndex()
        Manager.connectServer(player, InetSocketAddress(ip, port))
    }
}