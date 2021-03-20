package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf

data class PingInfo(
    val name: String, val map: String, val players: Int, val wave: Int,
    val version: Int, val verType: String, val mode: GameMode,
    val limit: Int, val desc: String, val modeName: String
) : Packet() {
    enum class GameMode {
        Survival, Sandbox, Attack, Pvp, Editor
    }

    var ping = -1

    override val factory: Factory<out Packet> get() = Companion

    //Needn't register this
    companion object : Factory<PingInfo>(null) {
        override fun decode(buf: ByteBuf): PingInfo {
            return PingInfo(
                buf.readString(byte = true), buf.readString(byte = true), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readString(byte = true), GameMode.values()[buf.readByte().toInt()],
                buf.readInt(), buf.readString(byte = true), buf.readString(byte = true)
            )
        }

        override fun encode(buf: ByteBuf, obj: PingInfo) {
            buf.writeString(obj.name, byte = true)
            buf.writeString(obj.map, byte = true)
            buf.writeInt(obj.players)
            buf.writeInt(obj.wave)
            buf.writeInt(obj.version)
            buf.writeString(obj.verType, byte = true)
            buf.writeByte(obj.mode.ordinal)
            buf.writeInt(obj.limit)
            buf.writeString(obj.desc, byte = true)
            buf.writeString(obj.modeName, byte = true)
        }
    }
}