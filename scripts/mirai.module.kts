@file:DependsModule("coreLibrary")
@file:MavenDepends("net.mamoe:mirai-core:1.2.2", single = false)
@file:MavenDepends("net.mamoe:mirai-core-qqandroid:1.2.2", single = false)

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.DefaultLogger
import net.mamoe.mirai.utils.DefaultLoginSolver
import net.mamoe.mirai.utils.PlatformLogger

addDefaultImport("mirai.lib.*")
addLibraryByClass("net.mamoe.mirai.Bot")
addDefaultImport("net.mamoe.mirai.Bot")
addDefaultImport("net.mamoe.mirai.event.*")
addDefaultImport("net.mamoe.mirai.event.events.*")
addDefaultImport("net.mamoe.mirai.message.*")
addDefaultImport("net.mamoe.mirai.message.data.*")
addDefaultImport("net.mamoe.mirai.contact.*")
generateHelper()

val enable by config.key(false, "是否启动机器人(开启前先设置账号密码)")
val qq by config.key(1849301538L, "机器人qq号")
val password by config.key("123456", "机器人qq密码")

val channel = Channel<String>()

onEnable {
    if (!enable) {
        println("机器人未开启,请先修改配置文件")
        return@onEnable
    }
    DefaultLogger = { tag ->
        object : PlatformLogger() {
            override fun info0(message: String?, e: Throwable?) {
                if (tag?.startsWith("Bot") == true)
                    super.info0(message, e)
            }

            override fun info0(message: String?) {
                if (tag?.startsWith("Bot") == true)
                    super.info0(message)
            }

            override fun debug0(message: String?): Unit = Unit
            override fun debug0(message: String?, e: Throwable?): Unit = Unit
            override fun verbose0(message: String?): Unit = Unit
            override fun verbose0(message: String?, e: Throwable?): Unit = Unit
        }
    }
    val bot = Bot(qq, password) {
        fileBasedDeviceInfo(Config.dataDirectory.resolve("miraiDeviceInfo.json").absolutePath)
        parentCoroutineContext = coroutineContext
        loginSolver = DefaultLoginSolver(channel::receive)
    }
    logger.info("若出现验证需要输入信息,请使用重定向指令/sa mirai [要输入的内容]")
    launch {
        bot.login()
    }
}

Commands.controlCommand.let {
    it += CommandInfo(this, "mirai", "重定向输入到mirai") {
        usage = "[args...]"
        permission = "mirai.input"
        body {
            channel.sendBlocking(arg.joinToString(" "))
        }
    }
}