package mindustryProxy

import io.netty.buffer.Unpooled
import io.netty.channel.socket.DatagramPacket
import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.packet.PingInfo
import mindustryProxy.lib.protocol.UDPMultiplex
import mindustryProxy.lib.protocol.UpStreamConnection
import java.util.concurrent.atomic.AtomicBoolean

val ping = AtomicBoolean(false)
var lastResult: PingInfo? = null
var lastTime = 0L

val cacheTime by config.key(2000, "ping 缓存时间,单位ms")

listenTo<PingEvent> {
    if (lastResult == null)
        lastResult = result
    val server = Manager.defaultServer
    if (System.currentTimeMillis() - lastTime > cacheTime && ping.compareAndSet(false, true)) {
        cancelled = true
        Server.launch {
            val result = try {
                UpStreamConnection.ping(server)?.also {
                    lastResult = it
                    lastTime = System.currentTimeMillis()
                } ?: return@launch
            } catch (e: Exception) {
                return@launch logger.warning("fail to ping ${server.hostName}: $e")
            } finally {
                ping.set(false)
            }
            val resp = PingInfo.encode(Unpooled.directBuffer(), result)
            UDPMultiplex.send(DatagramPacket(resp, addr))
        }
    } else result = lastResult!!
}