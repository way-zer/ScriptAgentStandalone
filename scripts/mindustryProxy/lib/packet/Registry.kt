package mindustryProxy.lib.packet

import io.netty.buffer.*
import mindustryProxy.lib.Server
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

abstract class Packet {
    var udp = false
    abstract val factory: Factory<out Packet>

    abstract class Factory<T : Packet>(val packetId: Int?) {
        fun ByteBuf.toByteArray(): ByteArray {
            val out = ByteArray(readableBytes())
            readBytes(out)
            return out
        }

        fun ByteBuf.readStringB(): String {
            val b = readBoolean()
            if (!b) return ""
            return ByteBufInputStream(this).readUTF()
        }

        fun ByteBuf.writeStringB(str: String) {
            if (str.isEmpty())
                writeBoolean(false)
            else {
                writeBoolean(true)
                ByteBufOutputStream(this).writeUTF(str)
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
    private val decompressor: LZ4FastDecompressor = LZ4Factory.fastestInstance().fastDecompressor()
    private val compressor: LZ4Compressor = LZ4Factory.fastestInstance().fastCompressor()


    fun register(factory: Packet.Factory<*>) {
        if (factory.packetId == null) return
        idMap[factory.packetId] = factory
    }

    fun decodeWithId(packetId: Int, buf: ByteBuf): Packet {
        if (packetId == FrameworkMessage.packetId)
            return FrameworkMessage.decode(buf)
        if (packetId == ConnectPacket.packetId && buf.getInt(buf.readerIndex()) in 105..126) {
            buf.release()
            throw UnsupportedOperationException("Not Support 126")
        }
        return if (packetId in idMap) {
            val length = buf.readShort().toInt()
            val compression = buf.readBoolean()
            val decompressed = if (!compression) buf.readRetainedSlice(length)
            else Unpooled.buffer(length, length).also {
                decompressor.decompress(
                    buf.nioBuffer(),
                    it.nioBuffer(it.writerIndex(), length)
                )
                it.writerIndex(it.writerIndex() + length)
            }
            try {
                idMap[packetId]!!.decode(decompressed)
            } finally {
                decompressed.release()
            }
        } else {
            UnknownPacket(packetId, buf.retainedSlice())
        }
    }

    fun decode(buf: ByteBuf): Packet {
        try {
            return decodeWithId(buf.readByte().toInt(), buf)
        } catch (e: UnsupportedOperationException) {
            throw e
        } catch (e: Exception) {
            buf.readerIndex(0)
            Server.logger.warning("Fail to decode packet: \n${ByteBufUtil.hexDump(buf)}")
            buf.release()
            throw e
        }
    }

    fun decodeOnlyFrameworkMessage(buf: ByteBuf): FrameworkMessage? {
        val id = buf.readByte().toInt()
        if (id != FrameworkMessage.packetId) {
            buf.readerIndex(buf.readerIndex() - 1)
            return null
        }
        return FrameworkMessage.decode(buf)
    }

    fun encode(buf: ByteBuf, obj: Packet) {
        if (obj is FrameworkMessage) {
            buf.writeByte(FrameworkMessage.packetId!!)
            FrameworkMessage.encode(buf, obj)
            return
        }
        if (obj is UnknownPacket) {
            obj.factory.encodeUnsafe(buf, obj)
            return
        }
        val factory = obj.factory
        factory.packetId?.let(buf::writeByte)

        val begin = buf.writerIndex()
        buf.writeShort(0)//to replace
        buf.writeBoolean(false) //TODO handle compressed Packet
        factory.encodeUnsafe(buf, obj)
        //replace length short
        val len = buf.writerIndex() - begin - 3
        buf.slice(begin, 2).apply {
            writerIndex(0)
            writeShort(len)
        }
    }

    init {
        register(FrameworkMessage)
        register(StreamBegin)
        register(StreamChunk)
        register(WorldStream)
        register(ConnectPacket)
//        register(InvokePacket)// delete in 127
//        register(UnknownPacket) // as fallback
    }
}