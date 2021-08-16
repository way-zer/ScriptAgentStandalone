@file:Depends("coreLibrary")
@file:Import("io.ktor:ktor-server-jetty:1.6.2", mavenDepends = true)
@file:Import("com.fasterxml.jackson.core:jackson-databind:2.12.3", mavenDependsSingle = true)
@file:Import("com.fasterxml.jackson.core:jackson-core:2.12.3", mavenDependsSingle = true)
@file:Import("com.fasterxml.jackson.core:jackson-annotations:2.12.3", mavenDependsSingle = true)
@file:Import("io.ktor:ktor-jackson:1.6.2", mavenDepends = true)
@file:Import("javax.servlet:javax.servlet-api:3.1.0", mavenDependsSingle = true)
@file:Import("ktor.lib.*", defaultImport = true)
@file:Import("io.ktor.application.*", defaultImport = true)
@file:Import("io.ktor.http.*", defaultImport = true)
@file:Import("io.ktor.routing.*", defaultImport = true)
@file:Import("io.ktor.request.*", defaultImport = true)
@file:Import("io.ktor.response.*", defaultImport = true)

import cf.wayzer.scriptAgent.events.ScriptEnableEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.event.Level

val port by config.key(9090, "Web 端口")
generateHelper()


var server: ApplicationEngine? = null

fun restart() {
    server?.stop(1000, 5000)
    server = embeddedServer(Jetty, port = port, parentCoroutineContext = this.coroutineContext) {
        //uninstallAllFeatures() //useless in current ktor, restart fully
        install(ContentNegotiation) {
            jackson {
                val module = SimpleModule()
                module.addDeserializer(Parameters::class.java, object : JsonDeserializer<Parameters>() {
                    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Parameters {
                        return Parameters.build {
                            while (true)
                                append(p.nextFieldName() ?: break, p.nextTextValue())
                        }
                    }

                })
                registerModule(module)
            }
        }
        install(CallLogging) {
            level = Level.INFO
        }
        routing {
            get("/testEnable") {
                call.respond(enabled.toString())
            }
            RouteHelper.root.set(this)
        }
        ScriptManager.allScripts.values.forEach { s ->
            s.inst?.webInit?.forEach { it.invoke(this);true }
        }
    }.start(false)
}

var job: Job? = null
fun prepareRestart() {
    job?.cancel()
    job = launch {
        delay(5_000)
        println("重启Web服务器")
        restart()
    }
}

onEnable {
    prepareRestart()
}
listenTo<ScriptEnableEvent> {
    if (script.webInit.getData().isEmpty()) return@listenTo
    prepareRestart()
}
onDisable {
    server?.stop(1000, 5000)
}