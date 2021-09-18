@file:Import("org.jline:jline-terminal-jansi:3.19.0", mavenDependsSingle = true)
@file:Import("org.jline:jline-terminal:3.19.0", mavenDependsSingle = true)
@file:Import("org.fusesource.jansi:jansi:2.1.0", mavenDependsSingle = true)
@file:Import("org.jline:jline-reader:3.19.0", mavenDependsSingle = true)

package coreStandalone

import org.jline.reader.*
import java.io.InterruptedIOException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object MyCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        candidates += RootCommands.tabComplete(reader.buffer.toString().split(' ')).map {
            Candidate(it)
        }
    }
}

lateinit var thisT: Thread

onEnable {
    thisT = thread(true, isDaemon = true, contextClassLoader = javaClass.classLoader, name = "Console Reader") {
        val arr = arrayOfNulls<Thread>(Thread.activeCount())
        Thread.enumerate(arr)
        arr.filter { (it?.name == "Server Controls" || it?.name == "Console Reader") && it != thisT }.forEach {
            it!!.interrupt()
            if (it.name == "Server Controls") {
                //Thread "Server Controls" don't have any point to interrupt. Only stop
                @Suppress("DEPRECATION")
                it.stop()
            }
            it.join()
        }
        val reader = LineReaderBuilder.builder()
            .completer(MyCompleter).build() as LineReader
        var last = 0
        while (!Thread.interrupted()) {
            val line = try {
                reader.readLine("> ").let(RootCommands::trimInput)
            } catch (e: InterruptedIOException) {
                continue
            } catch (e: UserInterruptException) {
                if (last != 1) {
                    println("Interrupt again to exit application")
                    last = 1
                    continue
                }
                println("exit")
                ScriptManager.disableAll()
                exitProcess(255)
            } catch (e: EndOfFileException) {
                if (last != 2) {
                    println("Catch EndOfFile, again to reload all script")
                    last = 2
                    continue
                }
                println("Catch EndOfFile, reload all script")
                ScriptManager.disableAll()
                ScriptManager.allScripts.filterKeys { Config.isModule(it) }.values.forEach {
                    it.savedField = emptyMap()
                    ScriptManager.loadScript(it)
                }
                ScriptManager.enableAll()
                return@thread
            }
            last = 0
            if (line.isEmpty()) continue
            try {
                RootCommands.handleInput(line)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}