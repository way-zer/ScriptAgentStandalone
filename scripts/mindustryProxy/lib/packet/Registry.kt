package mindustryProxy.lib.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.util.ReferenceCountUtil
import mindustryProxy.lib.Server
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

abstract class Packet {
    var udp = false
    abstract val factory: Factory<out Packet>

    abstract class Factory<T : Packet>(val packetId: Int?) {
        fun ByteBuf.readByteArray(length: Int = readableBytes()): ByteArray {
            val out = ByteArray(length)
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

        fun ByteBuf.writeString(str: String, byte: Boolean = false) {
            val bs = str.toByteArray()
            if (byte) writeByte(bs.size)
            else writeShort(bs.size)
            writeBytes(bs)
        }

        /**note: No Need to do [ByteBuf.release]*/
        abstract fun decode(buf: ByteBuf): T
        abstract fun encode(buf: ByteBuf, obj: T): ByteBuf

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

    @Throws(Exception::class)
    fun decodeWithId(packetId: Int, buf: ByteBuf): Packet {
        if (packetId == FrameworkMessage.packetId)
            try {
                return FrameworkMessage.decode(buf)
            } finally {
                buf.release()
            }
        if (packetId == ConnectPacket.packetId && buf.getInt(buf.readerIndex()) in 105..126) {
            buf.release()
            throw UnsupportedOperationException("Not Support 126")
        }
        return idMap[packetId]?.let { factory ->
            val length = buf.readShort().toInt()
            val compression = buf.readBoolean()
            val decompressed = if (!compression) buf.readSlice(length)
            else buf.alloc().directBuffer(length, length).also {
                decompressor.decompress(
                    buf.nioBuffer(),
                    it.nioBuffer(it.writerIndex(), length)
                )
                it.writerIndex(it.writerIndex() + length)
            }
            try {
                factory.decode(decompressed)
            } finally {
                decompressed.release()
            }
        } ?: UnknownPacket(packetId, buf.slice())
    }

    @Throws(Exception::class)
    fun decode(buf: ByteBuf): Packet {
        buf.touch("Registry.decode")
        buf.retain()//for hexDump
        try {
            return decodeWithId(buf.readByte().toInt(), buf)
        } catch (e: UnsupportedOperationException) {
            throw e
        } catch (e: Exception) {
            buf.readerIndex(0)
            Server.logger.warning("Fail to decode packet: \n${ByteBufUtil.hexDump(buf)}")
            throw e
        } finally {
            buf.release()
        }
    }

    fun decodeOnlyFrameworkMessage(buf: ByteBuf): FrameworkMessage? {
        try {
            val id = buf.readByte().toInt()
            if (id != FrameworkMessage.packetId) {
                buf.readerIndex(buf.readerIndex() - 1)
                return null
            }
            return FrameworkMessage.decode(buf)
        } finally {
            buf.release()
        }
    }

    /**
     * Unsafe: no release for [obj]
     * @see [encode]
     */
    private fun encodeUnsafe(buf: ByteBuf, obj: Packet): ByteBuf {
        when (obj) {
            is FrameworkMessage -> {
                buf.writeByte(FrameworkMessage.packetId!!)
                return FrameworkMessage.encode(buf, obj)
            }

            is UnknownPacket -> {
                return obj.factory.encodeUnsafe(buf, obj)
            }
            else -> {
                val factory = obj.factory
                factory.packetId?.let(buf::writeByte)
                //header: length and compressed, replace later
                val lengthIndex = buf.writerIndex()
                buf.writeShort(0)//length, replace later
                buf.writeBoolean(false)
                val data = factory.encodeUnsafe(buf, obj)
                val rawLen = data.writerIndex() - lengthIndex - 3
                var compress = obj !is StreamChunk && rawLen > 100
                if (compress) {
                    data.writerIndex(lengthIndex + 3)
                    val dstBuf = data.alloc().heapBuffer(compressor.maxCompressedLength(rawLen))
                    try {
                        val src = data.nioBuffer(data.writerIndex(), rawLen)
                        val dst = dstBuf.writerIndex(compressor.maxCompressedLength(rawLen)).nioBuffer()
                        val cpLen = compressor.compress(
                            src, src.position(), src.remaining(),
                            dst, dst.position(), dst.remaining()
                        )
                        if (cpLen < rawLen - 10)
                            data.writeBytes(dstBuf, cpLen)
                        else
                            compress = false
                    } finally {
                        dstBuf.release()
                    }
                }
                return data.also {
                    it.slice(lengthIndex, 3).apply {
                        writerIndex(0)
                        writeShort(rawLen)
                        writeBoolean(compress)
                    }
                }
            }
        }
    }

    /**
     * encode [obj] into [buf]
     */
    fun encode(buf: ByteBuf, obj: Packet) = try {
        buf.touch("Registry.encode")
        encodeUnsafe(buf, obj)
    } catch (e: Throwable) {
        buf.release()
        throw e
    } finally {
        ReferenceCountUtil.release(obj)
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