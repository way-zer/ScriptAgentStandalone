package cf.wayzer.scriptAgent

import cf.wayzer.scriptAgent.define.LoaderApi
import java.io.File
import java.net.URL
import java.net.URLClassLoader

object Main {
    @JvmStatic
    @OptIn(LoaderApi::class)
    fun main() {
        URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java).apply {
            isAccessible = true
        }.invoke(javaClass.classLoader, File("nativeLibs").toURI().toURL())
        ScriptManager.loadDir(File("scripts").apply { mkdir() })
        println("Finished!")
        while (true) Thread.sleep(60_000)
    }
}