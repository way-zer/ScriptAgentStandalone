package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import mindustryProxy.lib.Server

abstract class Packet {
    var udp = false
    abstract val factory: Factory<out Packet>

    abstract class Factory<T : Packet>(val packetId: Int?) {
        fun ByteBuf.toByteArray(): ByteArray {
            if (hasArray()) return array()
            val tmp = Unpooled.buffer(readableBytes(), readableBytes())
            tmp.writeBytes(this)
            return tmp.array().also {
                tmp.release()
            }
        }

        fun ByteBuf.readString(byte: Boolean = false): String {
            val length = if (byte) readByte().toInt() else readShort().toInt()
            return if (length <= 0) ""
            else readSlice(length).toString(Charsets.UTF_8)
        }

        fun ByteBuf.writeString(str: String, byte: Boolean = false): ByteBuf {
            val bs = str.toByteArray()
            if (byte) writeByte(bs.size)
            else writeShort(bs.size)
            writeBytes(bs)
            return this
        }

        abstract fun decode(buf: ByteBuf): T
        abstract fun encode(buf: ByteBuf, obj: T)

        @Suppress("UNCHECKED_CAST")
        fun encodeUnsafe(buf: ByteBuf, obj: Packet) = encode(buf, obj as T)
    }
}

object Registry {
    private val idMap = mutableMapOf<Int, Packet.Factory<out Packet>>()
    fun register(factory: Packet.Factory<*>) {
        if (factory.packetId == null) return
        idMap[factory.packetId] = factory
    }

    fun decodeWithId(packetId: Int, buf: ByteBuf): Packet {
        val factory = idMap[packetId] ?: error("Can't find factory for packet(id=$packetId)")
        return factory.decode(buf)
    }

    fun decode(buf: ByteBuf): Packet {
        try {
            return decodeWithId(buf.readByte().toInt(), buf)
        } catch (e: Exception) {
            buf.readerIndex(0)
            Server.logger.warning("Fail to decode packet: \n${ByteBufUtil.hexDump(buf)}")
            throw e
        }
    }

    fun encode(buf: ByteBuf, obj: Packet) {
        val factory = obj.factory
        factory.packetId?.let(buf::writeByte)
        factory.encodeUnsafe(buf, obj)
    }

    init {
        register(FrameworkMessage)
        register(StreamBegin)
        register(StreamChunk)
        register(WorldStream)
        register(ConnectPacket)
        register(InvokePacket)
    }
}