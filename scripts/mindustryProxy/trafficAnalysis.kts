package mindustryProxy

import mindustryProxy.lib.event.PlayerPacketEvent
import mindustryProxy.lib.packet.StreamChunk
import mindustryProxy.lib.packet.UnknownPacket

data class Data(val id: Int, val name: String, var traffic: Int = 0, var packetNum: Int = 0) : Comparable<Data> {
    override fun compareTo(other: Data): Int {
        return compareValuesBy(this, other,
            { it.traffic },
            { it.packetNum }
        )
    }

    fun reset() {
        traffic = 0
        packetNum = 0
    }

    override fun toString(): String {
        return "${id.toString().padStart(2)}  ${name.padEnd(30)}: ${traffic / 1000}KB(P$packetNum)"
    }
}

var data = emptyArray<Data>()
var beginTime = System.currentTimeMillis()

onEnable {
    val list = sourceFile.resolveSibling("res/remoteId.csv").useLines { lines ->
        lines.associate {
            val sp = it.split("\t")
            sp[0].toInt() to sp[1]
        }
    }
    if (list.isEmpty()) return@onEnable
    data = Array(list.keys.maxOrNull()!! + 1) {
        Data(it, list[it] ?: "Unknown")
    }
}

listenTo<PlayerPacketEvent> {
    if (data.isEmpty()) return@listenTo
    when (val packet = packet) {
        is UnknownPacket -> {
            if (packet.typeId >= data.size) return@listenTo
            data[packet.typeId].apply {
                traffic += packet.data.readableBytes()
                packetNum++
            }
        }
        is StreamChunk -> {
            data[packet.factory.packetId!!].apply {
                traffic += packet.data.readableBytes()
                packetNum++
            }
        }
    }
}

command("traffic", "查看InvokePacket流量") {
    usage = "[reset]"
    body {
        if (data.isEmpty())
            returnReply("[red]资源csv文件未加载".with())
        if (arg.isNotEmpty() && arg[0] == "reset") {
            data.forEach(Data::reset)
            beginTime = System.currentTimeMillis()
            reply("[green]Reset finish".with())
        } else {
            val list = data.sortedDescending()
                .subList(0, 20)
                .map { it.toString() }
            reply(
                "InvokePacket Traffic in last {time}s:\n{list:\n}".with(
                    "time" to (System.currentTimeMillis() - beginTime) / 1000,
                    "list" to list
                )
            )
        }
    }
}