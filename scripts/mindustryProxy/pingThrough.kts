package mindustryProxy

import mindustryProxy.lib.event.PingEvent
import mindustryProxy.lib.packet.PingInfo
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
        try {
            UpStreamConnection.ping(server)?.let {
                lastResult = it
                lastTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            logger.warning("fail to ping ${server.hostName}: ${e.localizedMessage}")
        } finally {
            ping.set(false)
        }
    }
    result = lastResult!!
}