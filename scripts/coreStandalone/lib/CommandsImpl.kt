package coreStandalone.lib

import cf.wayzer.script_agent.ISubScript
import coreLibrary.lib.ColorApi
import coreLibrary.lib.CommandContext
import coreLibrary.lib.CommandInfo
import coreLibrary.lib.Commands

object RootCommands : Commands() {
    fun tabComplete(args: List<String>): List<String> {
        var result: List<String> = emptyList()
        try {
            onComplete(CommandContext().apply {
                reply = {}
                replyTabComplete = { result = it;CommandInfo.Return() }
                arg = args
            })
        } catch (e: CommandInfo.Return) {
        }
        return result
    }

    fun trimInput(text: String) = buildString {
        var start = 0
        var end = text.length - 1
        while (start < text.length && text[start] == ' ') start++
        while (end >= 0 && text[end] == ' ') end--
        var lastBlank = false
        for (i in start..end) {
            val nowBlank = text[i] == ' '
            if (!lastBlank || !nowBlank)
                append(text[i])
            lastBlank = nowBlank
        }
    }

    /**
     * @param text 输入字符串，应当经过trimInput处理
     * @param prefix 指令前缀,例如'/'
     */
    fun handleInput(text: String, prefix: String = "") {
        if (text.isEmpty()) return
        RootCommands.invoke(CommandContext().apply {
            hasPermission = { true }
            reply = { println(ColorApi.handle(it.toString(), ColorApi::consoleColorHandler)) }
            this.prefix = if (prefix.isEmpty()) "* " else prefix
            this.arg = text.removePrefix(prefix).split(' ')
        })
    }
}

fun ISubScript.command(
    name: String,
    description: String,
    init: CommandInfo.() -> Unit = {}
) {
    RootCommands.addSub(CommandInfo(this, name, description, init))
}