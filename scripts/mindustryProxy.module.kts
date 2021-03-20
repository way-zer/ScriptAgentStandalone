@file:Depends("coreStandalone")
@file:Import("io.netty:netty-all:4.1.60.Final", mavenDepends = true)
@file:Import("mindustryProxy.lib.*", defaultImport = true)

import mindustryProxy.lib.Manager
import mindustryProxy.lib.Server
import java.net.InetSocketAddress

val port by config.key(6567, "监听端口", "重启生效")
config.key("server", "mdt.wayzer.cf", "默认服务器") {
    val ip = it.split(':').first()
    val port = it.split(':').getOrNull(1)?.toIntOrNull() ?: 6567
    Manager.defaultServer = InetSocketAddress(ip, port)
}
generateHelper()

onEnable {
    Server.logger = logger
    Server.start(port)
}
onDisable {
    Server.stop()
}