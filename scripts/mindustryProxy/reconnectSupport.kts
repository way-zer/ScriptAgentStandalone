package mindustryProxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.UnknownPacket
import java.net.InetSocketAddress

listenTo<PlayerPacketEvent> {
    val packet = packet
    if (packet is UnknownPacket && packet.typeId == 14/*ConnectCallPacket*/) {
        cancelled = true
        fun ByteBuf.readStr(): String {
            return if (readBoolean()) {
                ByteBufInputStream(this).readUTF()
            } else ""
        }
        with(packet.data) {
            markReaderIndex()
            readShort()//length
            assert(!readBoolean())//compression
            val ip = readStr()
            val port = readInt()
            resetReaderIndex()
            Manager.connectServer(player, InetSocketAddress(ip, port))
        }
    }
}