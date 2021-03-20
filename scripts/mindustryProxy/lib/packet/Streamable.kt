package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class StreamBegin(val id: Int, val total: Int, val type: Int) : Packet() {
    private var buildBuf: ByteBuf? = null
    val needBuild get() = buildBuf != null
    fun build() {
        if (buildBuf == null) buildBuf = Unpooled.buffer(total, total)
    }

    fun tryBuild(packet: StreamChunk): Streamable? {
        if (packet.id == this.id) {
            buildBuf?.writeBytes(packet.data)
            if (buildBuf?.readableBytes() == total)
                return Streamable(buildBuf!!)
        }
        return null
    }

    override val factory: Factory<out Packet> get() = Companion

    companion object : Factory<StreamBegin>(0) {
        override fun decode(buf: ByteBuf): StreamBegin {
            return StreamBegin(buf.readInt(), buf.readInt(), buf.readByte().toInt())
        }

        override fun encode(buf: ByteBuf, obj: StreamBegin) {
            buf.writeInt(obj.id)
            buf.writeInt(obj.total)
            buf.writeByte(obj.type)
        }
    }
}

class StreamChunk(val id: Int, val data: ByteBuf) : Packet() {
    override val factory: Factory<out Packet> get() = Companion

    companion object : Factory<StreamChunk>(1) {
        override fun decode(buf: ByteBuf): StreamChunk {
            return StreamChunk(buf.readInt(), buf.readRetainedSlice(buf.readShort().toInt()).asReadOnly())
        }

        override fun encode(buf: ByteBuf, obj: StreamChunk) {
            buf.writeInt(obj.id)
            buf.writeShort(obj.data.readableBytes())
            buf.writeBytes(obj.data)
        }
    }
}

open class Streamable(val stream: ByteBuf) : Packet() {
    override val factory: Factory<out Packet> get() = error("Streamable handle in PacketHandler")
}

class WorldStream(stream: ByteBuf) : Streamable(stream) {
    companion object : Factory<WorldStream>(2) {
        override fun decode(buf: ByteBuf): WorldStream = WorldStream(buf.retainedSlice().asReadOnly())
        override fun encode(buf: ByteBuf, obj: WorldStream) = error("Streamable handle in PacketHandler")
    }
}