package ktor.lib

import cf.wayzer.scriptAgent.define.ISubScript
import cf.wayzer.scriptAgent.events.ScriptDisableEvent
import cf.wayzer.scriptAgent.events.ScriptEnableEvent
import cf.wayzer.scriptAgent.getContextScript
import cf.wayzer.scriptAgent.listenTo
import cf.wayzer.scriptAgent.util.DSLBuilder
import coreLibrary.lib.util.Provider
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import ktor.lib.RouteHelper.routes

object RouteHelper {
    data class RouteInfo(val path: String, val method: HttpMethod?, val body: Route.() -> Unit) {
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
            var route = root.createRouteFromPath(info.path)
            if (info.method != null)
                route = route.createChild(HttpMethodRouteSelector(info.method))
            info.route = route
            val beforeR = route.subTree()
            val beforeH = route.handlers.toSet()
            info.body.invoke(route)
            info.subRoute = (route.subTree() - beforeR).values.takeIf { it.isNotEmpty() }.orEmpty()
            info.handler = (route.handlers.toMutableSet() - beforeH).takeIf { it.isNotEmpty() }.orEmpty()
        }
    }

    init {
        RouteHelper::class.java.getContextScript().apply {
            listenTo<ScriptEnableEvent>(4) {
                root.listenWithAutoCancel(script) {
                    script.initRoute(it)
                }
            }
            listenTo<ScriptDisableEvent> {
                val routes = routes_key.run { script.get() } ?: return@listenTo
                if (routes.isNotEmpty() && routes.first()::class.java != RouteInfo::class.java) return@listenTo//Class not same after ktor module reload
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
 * 调用方法:
 *   route("/xxx"){
 *      get{}
 *   }
 *   或
 *   route("/xxx",HttpMethod.Get){
 *      handle{}
 *   }
 */
@ContextDsl
fun ISubScript.route(path: String, method: HttpMethod? = null, body: Route.() -> Unit) {
    routes.add(RouteHelper.RouteInfo(path, method, body))
}