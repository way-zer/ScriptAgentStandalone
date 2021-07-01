package mindustryProxy

import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.StreamChunk
import mindustryProxy.lib.packet.UnknownPacket

var nameMap = emptyArray<String>()
var traffic = emptyArray<Int>()
var beginTime = System.currentTimeMillis()

onEnable {
    val list = sourceFile.resolveSibling("res/remoteId.csv").useLines { it.toList() }
    nameMap = Array(list.size) { "unknown" }
    traffic = Array(list.size) { 0 }
    list.forEach {
        val sp = it.split("\t")
        nameMap[sp[0].toInt()] = sp[1]
    }
}

listenTo<PlayerPacketEvent> {
    if (nameMap.isEmpty()) return@listenTo
    when (val packet = packet) {
        is UnknownPacket -> {
            if (packet.typeId >= nameMap.size) return@listenTo
            traffic[packet.typeId] += packet.data.readableBytes()
        }
        is StreamChunk -> {
            traffic[packet.factory.packetId!!] += packet.data.readableBytes()
        }
    }
}

command("traffic", "查看InvokePacket流量") {
    usage = "[reset]"
    body {
        if (nameMap.isEmpty())
            returnReply("[red]资源csv文件未加载".with())
        if (arg.isNotEmpty() && arg[0] == "reset") {
            traffic.fill(0)
            beginTime = System.currentTimeMillis()
            reply("[green]Reset finish".with())
        } else {
            val list = traffic.mapIndexed { index, i ->
                (index.toString().padStart(2) + " " + nameMap[index]) to i
            }.sortedByDescending { it.second }
                .map { (name, i) -> "$name:${i / 1024}KB" }
                .subList(0, 20)
            reply(
                "InvokePacket Traffic in last {time}s:\n{list:\n}".with(
                    "time" to (System.currentTimeMillis() - beginTime) / 1000,
                    "list" to list
                )
            )
        }
    }
}