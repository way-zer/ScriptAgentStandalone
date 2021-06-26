package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.util.ReferenceCounted

data class UnknownPacket(val typeId: Int, val data: ByteBuf) : Packet(), ReferenceCounted by data {
    override val factory: Factory<out Packet> get() = Companion

    override fun touch(hint: Any?): ReferenceCounted {
        data.touch(hint)
        return this
    }

    companion object : Factory<UnknownPacket>(null) {
        override fun decode(buf: ByteBuf): Nothing {
            throw UnsupportedOperationException() //special in Registry
        }

        override fun encode(buf: ByteBuf, obj: UnknownPacket) {
            buf.writeByte(obj.typeId)
            buf.writeBytes(obj.data)
            obj.data.resetReaderIndex()
        }
    }
}