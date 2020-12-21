package coreStandalone.lib

import cf.wayzer.script_agent.ISubScript
import coreLibrary.lib.CommandInfo
import coreLibrary.lib.Commands

object RootCommands : Commands()

fun ISubScript.command(
    name: String,
    description: String,
    init: CommandInfo.() -> Unit = {}
) {
    RootCommands.addSub(CommandInfo(this, name, description, init))
}