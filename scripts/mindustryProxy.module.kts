@file:Depends("coreStandalone")
@file:Import("io.netty:netty-all:4.1.60.Final", mavenDepends = true)
@file:Import("org.lz4:lz4-java:1.7.1", mavenDepends = true)
@file:Import("mindustryProxy.lib.*", defaultImport = true)

import io.netty.util.ResourceLeakDetector
import mindustryProxy.lib.packet.PacketIdMapper
import java.net.InetSocketAddress

val port by config.key(6567, "监听端口", "重启生效")
config.key("server", "mdt.wayzer.cf", "默认服务器") {
    val ip = it.split(':').first()
    val port = it.split(':').getOrNull(1)?.toIntOrNull() ?: 6567
    Manager.defaultServer = InetSocketAddress(ip, port)
}
config.key("leakDetectorLevel", ResourceLeakDetector.Level.SIMPLE, "Netty 泄漏探测级别") {
    ResourceLeakDetector.setLevel(it)
}
generateHelper()

onEnable {
    Server.logger = logger
    PacketIdMapper//load
    Server.start(port)
}
onDisable {
    Server.stop()
}

command("reloadId", "重载Packet Id文件") {
    permission = "mindustryProxy.reloadId"
    body {
        PacketIdMapper.load()
    }
}