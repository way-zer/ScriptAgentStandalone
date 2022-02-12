package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCounted
import mindustryProxy.lib.Server

open class StreamBegin(val id: Int, val total: Int, val type: Int) : Packet() {
    /**if true, the proxy will build all streamChunk together*/
    var needBuild = false

    class StreamBuilder(
        val id: Int, val total: Int, val type: Int
    ) {
        private val buildBuf = Unpooled.compositeBuffer()

        @Synchronized
        fun tryBuild(packet: StreamChunk): Streamable? {
            assert(packet.id == id) {
                packet.release()
                "StreamChunk must have the same id"
            }
            packet.touch("StreamBuilder")
            buildBuf.addComponent(true, packet.data.slice())
            if (buildBuf.readableBytes() > total)
                Server.logger.warning("StreamBuilder: buildBuf.readableBytes() > total")
            return if (buildBuf.readableBytes() == total)
                Streamable(buildBuf)
            else
                null
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

        override fun encode(buf: ByteBuf, obj: StreamBegin): ByteBuf {
            buf.writeInt(obj.id)
            buf.writeInt(obj.total)
            buf.writeByte(obj.type)
            return buf
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
            return StreamChunk(buf.readInt(), buf.readRetainedSlice(buf.readShort().toInt()))
        }

        override fun encode(buf: ByteBuf, obj: StreamChunk): ByteBuf {
            buf.writeInt(obj.id)
            buf.writeShort(obj.data.readableBytes())
            return Unpooled.compositeBuffer(2)
                .addComponents(true, buf, obj.data.retainedSlice())
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
        override fun decode(buf: ByteBuf): WorldStream = WorldStream(buf.retainedSlice())
        override fun encode(buf: ByteBuf, obj: WorldStream) = error("Streamable handle in PacketHandler")
    }
}