@file:DependsModule("coreLibrary")
@file:MavenDepends("io.ktor:ktor-server-jetty:1.4.0", single = false)
@file:MavenDepends("com.fasterxml.jackson.core:jackson-databind:2.10.1")
@file:MavenDepends("com.fasterxml.jackson.core:jackson-core:2.10.1")
@file:MavenDepends("com.fasterxml.jackson.core:jackson-annotations:2.10.1")
@file:MavenDepends("io.ktor:ktor-jackson:1.4.0", single = false)
@file:MavenDepends("javax.servlet:javax.servlet-api:3.1.0")

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import ktor.lib.RouteHelper
import ktor.lib.webInit
import org.slf4j.event.Level
import java.lang.ref.WeakReference

val port by config.key(9090, "Web 端口")
addLibraryByClass("io.ktor.application.Application")
addLibraryByClass("io.ktor.util.pipeline.Pipeline")
addLibraryByClass("io.ktor.http.HttpStatusCode")
addLibraryByClass("com.fasterxml.jackson.core.Version")
addLibraryByClass("com.fasterxml.jackson.databind.Module")
addDefaultImport("ktor.lib.*")
addDefaultImport("io.ktor.application.*")
addDefaultImport("io.ktor.http.*")
addDefaultImport("io.ktor.routing.*")
addDefaultImport("io.ktor.request.*")
addDefaultImport("io.ktor.response.*")
generateHelper()


val webChildren = mutableSetOf<WeakReference<ISubScript>>()

lateinit var application: Application
val server = embeddedServer(Jetty, port = port, parentCoroutineContext = this.coroutineContext) {
    application = this
    restart()
}

fun restart() {
    with(application) {
        uninstallAllFeatures()
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
        webChildren.removeIf { script ->
            script.get()?.apply {
                if (!enabled) return@apply
                webInit.forEach { it.invoke(this@with);true }
            }
            script.get() == null
        }
    }
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
    server.start(wait = false)
}
onAfterContentEnable {
    if (it.webInit.getData().isEmpty()) return@onAfterContentEnable
    webChildren.add(WeakReference(it))
    prepareRestart()
}
onDisable {
    server.stop(1000, 5000)
}