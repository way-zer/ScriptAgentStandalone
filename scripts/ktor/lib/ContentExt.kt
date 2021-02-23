package ktor.lib

import cf.wayzer.scriptAgent.define.ISubScript
import cf.wayzer.scriptAgent.util.DSLBuilder
import io.ktor.application.*

val ISubScript.webInit by DSLBuilder.callbackKey<Application.() -> Unit>()