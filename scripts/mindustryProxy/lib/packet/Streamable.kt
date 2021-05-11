package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCounted

open class StreamBegin(val id: Int, val total: Int, val type: Int) : Packet() {
    /**if true, the proxy will build all streamChunk together*/
    var needBuild = false

    class StreamBuilder(
        id: Int, total: Int, type: Int,
        private val buildBuf: ByteBuf = Unpooled.buffer(total, total)
    ) : StreamBegin(id, total, type) {
        var built: Streamable? = null

        @Synchronized
        fun tryBuild(packet: StreamChunk): Boolean {
            if (built != null) error("One Streamable can only build once")
            if (packet.id == this.id) {
                buildBuf.writeBytes(packet.data)
                if (buildBuf.readableBytes() == total) {
                    built = Streamable(buildBuf)
                    return true
                }
            }
            return false
        }

        fun release() {
            buildBuf.release()
        }
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

class StreamChunk(val id: Int, val data: ByteBuf) : Packet(), ReferenceCounted by data {
    override fun touch(hint: Any?): ReferenceCounted {
        data.touch(hint)
        return this
    }

    override val factory: Factory<out Packet> get() = Companion

    companion object : Factory<StreamChunk>(1) {
        override fun decode(buf: ByteBuf): StreamChunk {
            return StreamChunk(buf.readInt(), buf.readBytes(buf.readShort().toInt()))
        }

        override fun encode(buf: ByteBuf, obj: StreamChunk) {
            buf.writeInt(obj.id)
            buf.writeShort(obj.data.readableBytes())
            buf.writeBytes(obj.data)
        }
    }
}

open class Streamable(val stream: ByteBuf) : Packet(), ReferenceCounted by stream {
    override val factory: Factory<out Packet> get() = error("Streamable handle in PacketHandler")
    override fun touch(hint: Any?): ReferenceCounted {
        stream.touch(hint)
        return this
    }
}

class WorldStream(stream: ByteBuf) : Streamable(stream) {
    companion object : Factory<WorldStream>(2) {
        override fun decode(buf: ByteBuf): WorldStream = WorldStream(buf.readBytes(buf.readableBytes()))
        override fun encode(buf: ByteBuf, obj: WorldStream) = error("Streamable handle in PacketHandler")
    }
}