package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import java.util.*

data class ConnectPacket(
    val version: Int, val versionType: String,
    val name: String, val lang: String, val usid: String, val uuid: ByteArray,
    val mobile: Boolean, val color: Int, val mods: List<String>
) : Packet() {
    override val factory: Factory<out Packet> get() = Companion
    fun getUuidString(): String {
        //The last 8 bit is CRC32 for the first 8 bit
        return Base64.getEncoder().encodeToString(uuid.sliceArray(0 until 8))
    }

    companion object : Factory<ConnectPacket>(3) {
        override fun decode(buf: ByteBuf): ConnectPacket {
            return ConnectPacket(
                buf.readInt(), buf.readString(),
                buf.readString(), buf.readString(), buf.readString(), buf.readBytes(16).toByteArray(),
                buf.readBoolean(), buf.readInt(), List(buf.readByte().toInt()) { buf.readString() }
            )
        }

        override fun encode(buf: ByteBuf, obj: ConnectPacket) {
            buf.writeInt(obj.version)
            buf.writeString(obj.versionType)
            buf.writeString(obj.name)
            buf.writeString(obj.lang)
            buf.writeString(obj.usid)
            buf.writeBytes(obj.uuid)
            buf.writeBoolean(obj.mobile)
            buf.writeInt(obj.color)
            buf.writeByte(obj.mods.size)
            obj.mods.forEach {
                buf.writeString(it)
            }
        }
    }
}