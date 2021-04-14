package cf.wayzer.scriptAgent

import cf.wayzer.scriptAgent.define.LoaderApi
import cf.wayzer.scriptAgent.define.ScriptState
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.system.exitProcess

@OptIn(LoaderApi::class)
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java).apply {
            isAccessible = true
        }.invoke(javaClass.classLoader, File("nativeLibs").toURI().toURL())
        if (args.isNotEmpty()) generateMain(args)
        ScriptManager.loadDir(File("scripts").apply { mkdir() })
        println("Finished!")
        while (true) Thread.sleep(60_000)
    }

    @JvmStatic
    private fun generateMain(args: Array<String>) {
        Config.rootDir = File("scripts")
        var notFound = 0
        if (args.isEmpty())
            ScriptManager.loadDir(Config.rootDir, enable = false)
        else
            args.forEach {
                val script = ScriptManager.getScriptNullable(it)
                if (script == null) {
                    println("找不到脚本: $it")
                    notFound++
                    return@forEach
                }
                ScriptManager.loadScript(script, enable = false, children = false)
            }
        val fail = ScriptManager.allScripts.count { it.value.scriptState == ScriptState.Fail }
        if (notFound != 0)
            println("有${notFound}个输入脚本未找到")
        println("共加载${ScriptManager.allScripts.size}个脚本，失败${fail}个")
        exitProcess(notFound + fail)
    }
}