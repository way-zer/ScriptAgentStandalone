package ktor.lib

import cf.wayzer.script_agent.ISubScript
import cf.wayzer.script_agent.events.ScriptDisableEvent
import cf.wayzer.script_agent.events.ScriptEnableEvent
import cf.wayzer.script_agent.getContextModule
import cf.wayzer.script_agent.listenTo
import cf.wayzer.script_agent.util.DSLBuilder
import coreLibrary.lib.util.Provider
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import ktor.lib.RouteHelper.routes

object RouteHelper {
    data class RouteInfo(val path: String, val body: Route.() -> Unit) {
        var route: Route? = null
        var subRoute: Collection<Route> = emptyList()
        var handler = emptySet<Any>()
    }

    private val routes_key = DSLBuilder.DataKeyWithDefault("ktor_routes") { mutableSetOf<RouteInfo>() }
    val ISubScript.routes by routes_key
    val root = Provider<Route>()

    private val Route.handlers
        get() = Route::class.java.getDeclaredField("handlers").let {
            it.isAccessible = true
            it.get(this) as ArrayList<*>
        }

    private fun Route.removeCache() {
        Route::class.java.getDeclaredField("cachedPipeline").let {
            it.isAccessible = true
            it.set(this, null)
        }
    }

    private fun Route.subTree(): Map<String, Route> {
        return if (children.isEmpty()) mapOf(toString() to this)
        else {
            val map = mutableMapOf<String, Route>()
            children.forEach {
                map.putAll(it.subTree())
            }
            map
        }
    }

    private fun Route.removeSelf() {
        parent?.let { p ->
            val list = (p.children as MutableList<Route>)
            list.remove(this)
            if (list.isEmpty()) {
                if (p.handlers.isEmpty())
                    p.removeSelf()
            }
        }
    }

    private fun ISubScript.initRoute(root: Route) {
        val routes = routes_key.run { get() } ?: return
        routes.forEach { info ->
            val route = root.createRouteFromPath(info.path)
            info.route = route
            val beforeR = route.subTree()
            val beforeH = route.handlers.toSet()
            info.body.invoke(route)
            info.subRoute = (route.subTree() - beforeR).values.takeIf { it.isNotEmpty() }.orEmpty()
            info.handler = (route.handlers.toMutableSet() - beforeH).takeIf { it.isNotEmpty() }.orEmpty()
        }
    }

    init {
        RouteHelper::class.java.getContextModule()!!.apply {
            listenTo<ScriptEnableEvent>(4) {
                root.listenWithAutoCancel(script) {
                    script.initRoute(it)
                }
            }
            listenTo<ScriptDisableEvent> {
                val routes = routes_key.run { script.get() } ?: return@listenTo
                routes.forEach { info ->
                    val route = info.route ?: return@forEach
                    info.subRoute.forEach { it.removeSelf() }
                    val handler = route.handlers
                    handler.removeAll(info.handler)
                    if (route.children.isEmpty() && handler.isEmpty())
                        route.removeSelf()
                    else route.removeCache()
                }
            }
        }
    }
}

/**
 * 脚本Route帮助函数
 * path尽量使用终端节点
 * 避免与其他脚本使用相同的path，不然可能会出现错误
 */
@ContextDsl
fun ISubScript.route(path: String, body: Route.() -> Unit) {
    routes.add(RouteHelper.RouteInfo(path, body))
}