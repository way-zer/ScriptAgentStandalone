package mindustryProxy

import io.netty.buffer.ByteBuf
import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.Packet
import mindustryProxy.lib.packet.Registry

class SendChatMessageCallPacket(val message: String) : Packet() {
    companion object : Factory<SendChatMessageCallPacket>(52) {
        override fun decode(buf: ByteBuf): SendChatMessageCallPacket {
            return SendChatMessageCallPacket(buf.readStringB())
        }

        override fun encode(buf: ByteBuf, obj: SendChatMessageCallPacket) {
            buf.writeStringB(obj.message)
        }
    }

    override val factory: Factory<out Packet> get() = Companion
}

class SendMessageCallPacket(val message: String, val sender: String, val senderId: Int = -1) : Packet() {
    companion object : Factory<SendMessageCallPacket>(54) {
        override fun decode(buf: ByteBuf): SendMessageCallPacket {
            return SendMessageCallPacket(buf.readStringB(), buf.readStringB(), buf.readInt())
        }

        override fun encode(buf: ByteBuf, obj: SendMessageCallPacket) {
            buf.writeStringB(obj.message)
            buf.writeStringB(obj.sender)
            buf.writeInt(obj.senderId)
        }
    }

    override val factory: Factory<out Packet> get() = Companion
}

Registry.register(SendChatMessageCallPacket)
Registry.register(SendMessageCallPacket)

fun broadcast(from: String, text: String) {
    logger.info("[$from] $text")
    val packet = SendMessageCallPacket(text, from)
    Manager.players.forEach {
        it.clientCon.sendPacket(packet, false)
        it.clientCon.flush()
    }
}

listenTo<PlayerPacketEvent> {
    val packet = packet
    if (packet is SendChatMessageCallPacket) {
        if (packet.message.startsWith("/g ")) {
            val msg = packet.message.drop(2)
            cancelled = true
            broadcast("${player.server?.address?.hostName}|${player.connectPacket.name}", msg)
        }
    }
}

command("broadcast", "广播消息") {
    permission = "mindustryProxy.broadcast"
    usage = "<msg>"
    body {
        val msg = arg.joinToString(" ")
        broadcast("PROXY", msg)
    }
}