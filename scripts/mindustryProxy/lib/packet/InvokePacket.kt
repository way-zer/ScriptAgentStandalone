package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.util.ReferenceCounted

data class InvokePacket(val type: Int, val priority: Int, val data: ByteBuf) : Packet(), ReferenceCounted by data {
    override val factory: Factory<out Packet> get() = Companion

    override fun touch(hint: Any?): ReferenceCounted {
        data.touch(hint)
        return this
    }

    companion object : Factory<InvokePacket>(4) {
        override fun decode(buf: ByteBuf): InvokePacket {
            return InvokePacket(
                buf.readByte().toInt(),
                buf.readByte().toInt(),
                buf.readRetainedSlice(buf.readShort().toInt())
            )
        }

        override fun encode(buf: ByteBuf, obj: InvokePacket) {
            buf.writeByte(obj.type)
            buf.writeByte(obj.priority)
            buf.writeShort(obj.data.readableBytes())
            buf.writeBytes(obj.data)
            obj.data.resetReaderIndex()
        }
    }
}