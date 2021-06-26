package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import java.util.*

data class ConnectPacket(
    val version: Int, val versionType: String,
    val name: String, val lang: String, val usid: String,
    val uuid: ByteArray, val mobile: Boolean,
    val color: Int, val mods: List<String>
) : Packet() {
    override val factory: Factory<out Packet> get() = Companion
    fun getUuidString(): String {
        //The last 8 bit is CRC32 for the first 8 bit
        return Base64.getEncoder().encodeToString(uuid.sliceArray(0 until 8))
    }

    companion object : Factory<ConnectPacket>(3) {
        val NULL = ConnectPacket(
            -1, "null",
            "NULL", "null", "null", byteArrayOf(),
            false, 0, emptyList()
        )

        override fun decode(buf: ByteBuf): ConnectPacket {
            return ConnectPacket(
                buf.readInt(), buf.readStringB(),
                buf.readStringB(), buf.readStringB(), buf.readStringB(),
                buf.readSlice(16).toByteArray(), buf.readBoolean(),
                buf.readInt(), List(buf.readByte().toInt()) { buf.readStringB() }
            )
        }

        override fun encode(buf: ByteBuf, obj: ConnectPacket) {
            buf.writeInt(obj.version)
            buf.writeStringB(obj.versionType)
            buf.writeStringB(obj.name)
            buf.writeStringB(obj.lang)
            buf.writeStringB(obj.usid)
            buf.writeBytes(obj.uuid)
            buf.writeBoolean(obj.mobile)
            buf.writeInt(obj.color)
            buf.writeByte(obj.mods.size)
            obj.mods.forEach {
                buf.writeStringB(it)
            }
        }
    }
}