package ktor.lib

import cf.wayzer.script_agent.ISubScript
import cf.wayzer.script_agent.util.DSLBuilder
import io.ktor.application.*

val ISubScript.webInit by DSLBuilder.callbackKey<Application.() -> Unit>()