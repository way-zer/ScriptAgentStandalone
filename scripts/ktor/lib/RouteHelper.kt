package ktor.lib

import cf.wayzer.scriptAgent.Event
import cf.wayzer.scriptAgent.define.ISubScript
import cf.wayzer.scriptAgent.events.ScriptDisableEvent
import cf.wayzer.scriptAgent.events.ScriptEnableEvent
import cf.wayzer.scriptAgent.getContextScript
import cf.wayzer.scriptAgent.listenTo
import cf.wayzer.scriptAgent.util.DSLBuilder
import coreLibrary.lib.util.ServiceRegistry
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import ktor.lib.RouteHelper.routes
import ktor.lib.util.RouteHandlerList

object RouteHelper {
    data class RouteInfo(val path: String, val method: HttpMethod?, val body: Route.() -> Unit) {
        var handlerList: RouteHandlerList? = null
    }

    private val routes_key = DSLBuilder.DataKeyWithDefault("ktor_routes") { mutableSetOf<RouteInfo>() }
    val ISubScript.routes by routes_key
    val root = ServiceRegistry<Route>()

    private fun ISubScript.initRoute(root: Route) {
        val routes = routes_key.run { get() } ?: return
        routes.forEach { info ->
            var route = root.createRouteFromPath(info.path)
            if (info.method != null)
                route = route.createChild(HttpMethodRouteSelector(info.method))
            val before = RouteHandlerList().collect(route)
            info.body.invoke(route)
            info.handlerList = RouteHandlerList().collect(route) - before
        }
    }

    init {
        RouteHelper::class.java.getContextScript().apply {
            listenTo<ScriptEnableEvent>(Event.Priority.After) {
                root.subscribe(script) {
                    script.initRoute(it)
                }
            }
            listenTo<ScriptDisableEvent> {
                val routes = routes_key.run { script.get() } ?: return@listenTo
                if (routes.isNotEmpty() && routes.first()::class.java != RouteInfo::class.java) return@listenTo//Class not same after ktor module reload
                routes.forEach { it.handlerList?.tryRemoveAll() }
            }
        }
    }
}

/**
 * 脚本Route帮助函数
 * note: 禁止对返回的Route使用intercept,否则可能造成错误
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