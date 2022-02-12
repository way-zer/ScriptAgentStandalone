package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf

sealed class FrameworkMessage(val typeId: Int) : Packet() {
    data class Ping(val id: Int, val reply: Boolean) : FrameworkMessage(0)
    object DiscoverHost : FrameworkMessage(1)
    object KeepAlive : FrameworkMessage(2)
    data class RegisterUDP(val id: Int) : FrameworkMessage(3)
    data class RegisterTCP(val id: Int) : FrameworkMessage(4)

    override val factory: Factory<out Packet> get() = Companion

    companion object : Factory<FrameworkMessage>(-2) {
        override fun decode(buf: ByteBuf): FrameworkMessage {
            return when (val type = buf.readByte().toInt()) {
                0 -> Ping(buf.readInt(), buf.readBoolean())
                1 -> DiscoverHost
                2 -> KeepAlive
                3 -> RegisterUDP(buf.readInt())
                4 -> RegisterTCP(buf.readInt())
                else -> error("can't decode FrameworkMessage with type=$type")
            }
        }

        override fun encode(buf: ByteBuf, obj: FrameworkMessage): ByteBuf {
            buf.writeByte(obj.typeId)
            when (obj) {
                is Ping -> {
                    buf.writeInt(obj.id)
                    buf.writeBoolean(obj.reply)
                }
                is RegisterUDP -> buf.writeInt(obj.id)
                is RegisterTCP -> buf.writeInt(obj.id)
                else -> Unit
            }
            return buf
        }
    }
}