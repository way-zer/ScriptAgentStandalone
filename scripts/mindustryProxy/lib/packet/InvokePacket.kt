package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf

data class InvokePacket(val type: Int, val priority: Int, val data: ByteBuf) : Packet() {
    override val factory: Factory<out Packet> get() = Companion

    companion object : Factory<InvokePacket>(4) {
        override fun decode(buf: ByteBuf): InvokePacket {
            return InvokePacket(
                buf.readByte().toInt(),
                buf.readByte().toInt(),
                buf.readRetainedSlice(buf.readShort().toInt()).asReadOnly()
            )
        }

        override fun encode(buf: ByteBuf, obj: InvokePacket) {
            buf.writeByte(obj.type)
            buf.writeByte(obj.priority)
            buf.writeShort(obj.data.readableBytes())
            buf.writeBytes(obj.data)
        }
    }
}