package coreStandalone.lib

import cf.wayzer.script_agent.IContentScript
import java.util.logging.Logger

val IContentScript.logger get() = Logger.getLogger(id.replace('/', '.'))!!