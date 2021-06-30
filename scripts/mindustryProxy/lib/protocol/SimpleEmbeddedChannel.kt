package mindustryProxy.lib.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.atomic.AtomicBoolean

abstract class SimpleEmbeddedChannel : EmbeddedChannel() {
    fun addBuffer(byteBuf: ByteBuf) = writeInbound(byteBuf)
    private val flushing = AtomicBoolean(false)
    override fun doWrite(`in`: ChannelOutboundBuffer) {
        if (`in`.isEmpty) return
        if (!flushing.compareAndSet(false, true)) return
        val coped = mutableListOf<ByteBuf>()
        try {
            while (true) {
                val buf = (`in`.current() ?: break) as ByteBuf
                coped.add(buf.retain())
                `in`.remove()
            }
        } catch (e: Throwable) {
            coped.forEach {
                ReferenceCountUtil.safeRelease(it)
            }
            throw e
        } finally {
            flushing.set(false)
        }
        writeToParent(coped)
    }

    protected abstract fun writeToParent(list: List<ByteBuf>)

    override fun doClose() {
        val old = isActive
        super.doClose()
        if (old)
            doClose0()
    }

    abstract fun doClose0()
}

//@Suppress("LeakingThis")
//abstract class SimpleEmbeddedChannel0 : AbstractChannel(null) {
//    private val buffers = ConcurrentLinkedQueue<ByteBuf>()
//    fun addBuffer(byteBuf: ByteBuf) {
//        this.buffers.add(byteBuf)
//        if(prepareRead.compareAndSet(false,true)){
//            eventLoop().execute {
//                read()
//            }
//        }
//    }
//
//    private val prepareRead = AtomicBoolean(false)
//    private val reading = AtomicBoolean(false)
//    override fun doBeginRead() {
//        prepareRead.set(false)
//        if (reading.compareAndSet(false, true)) {
//            try {
//                if (buffers.isEmpty()) return
//                while (true) {
//                    val buf = buffers.poll() ?: break
//                    pipeline().fireChannelRead(buf)
//                }
//                pipeline().fireChannelReadComplete()
//            } finally {
//                reading.set(false)
//            }
//        }
//    }
//
//    private val flushing = AtomicBoolean(false)
//    override fun doWrite(`in`: ChannelOutboundBuffer) {
//        if (`in`.isEmpty) return
//        if (!flushing.compareAndSet(false, true)) return
//        val coped = mutableListOf<ByteBuf>()
//        try {
//            while (true) {
//                val buf = (`in`.current() ?: break) as ByteBuf
//                coped.add(buf.retain())
//                `in`.remove()
//            }
//        } catch (e: Throwable) {
//            coped.forEach {
//                ReferenceCountUtil.safeRelease(it)
//            }
//            throw e
//        } finally {
//            flushing.set(false)
//        }
//        writeToParent(coped)
//    }
//
//    protected abstract fun writeToParent(list: List<ByteBuf>)
//
//    //misc
//    val metadata = ChannelMetadata(false)
//    override fun metadata(): ChannelMetadata = metadata
//    val config = DefaultChannelConfig(this)
//    override fun config(): ChannelConfig = config
//    override fun newUnsafe(): AbstractUnsafe {
//        return object : AbstractUnsafe() {
//            override fun connect(
//                remoteAddress: SocketAddress?,
//                localAddress: SocketAddress?,
//                promise: ChannelPromise?
//            ) {
//                throw  UnsupportedOperationException()
//            }
//        }
//    }
//
//    override fun isCompatible(loop: EventLoop?): Boolean = true
//    override fun doBind(localAddress: SocketAddress?) {
//        throw UnsupportedOperationException()
//    }
//
//    private val closed = AtomicBoolean(false)
//    override fun isActive(): Boolean = !closed.get()
//    override fun doClose() {
//        if (closed.compareAndSet(false, true)) {
//            doClose0()
//            while (true) {
//                val buf = buffers.poll() ?: break
//                buf.release()
//            }
//        }
//    }
//
//    abstract fun doClose0()
//
//    override fun doDisconnect() {
//        doClose()
//    }
//
//    override fun isOpen(): Boolean = isActive
//}