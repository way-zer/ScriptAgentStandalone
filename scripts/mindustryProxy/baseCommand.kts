package mindustryProxy

import mindustryProxy.lib.packet.ConnectPacket

command("players", "玩家分布情况") {
    body {
        val list = Manager.players.groupBy { it.server?.takeIf { i -> i.isActive }?.address }
            .map { (server, players) ->
                "{server}: {list:, }[white]".with(
                    "server" to (server?.toString() ?: "NONE"),
                    "list" to players.map { it.connectPacket.name }
                )
            }
        reply("[white]当前在线玩家{num}个: \n {list:\n}".with("num" to Manager.players.size, "list" to list))
    }
}

command("clearNull", "清理Null玩家") {
    body {
        val list = Manager.players.filter { it.connectPacket == ConnectPacket.NULL || it.server?.isActive == false }
        list.forEach {
            it.close()
        }
        reply("[green]Clear {num} NULL player: \n{ipList:\n}".with("num" to list.size, "ipList" to list.map {
            it.clientCon.address
        }))
    }
}