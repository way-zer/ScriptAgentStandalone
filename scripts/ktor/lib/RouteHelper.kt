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
    data class RouteInfo(val path: String, val body: Route.() -> Unit, var inst: Route?)

    private val routes_key = DSLBuilder.DataKeyWithDefault("ktor_routes") { mutableSetOf<RouteInfo>() }
    val ISubScript.routes by routes_key
    val root = Provider<Route>()

    private fun Route.removeSelf() {
        parent?.let { p ->
            val list = (p.children as MutableList<Route>)
            list.remove(this)
            if (list.isEmpty()) {
                val handlers = Route::class.java.getDeclaredField("handlers").let {
                    it.isAccessible = true
                    it.get(p) as ArrayList<*>
                }
                if (handlers.isEmpty())
                    p.removeSelf()
            }
            Route::class.java.getDeclaredField("cachedPipeline").let {
                it.isAccessible = true
                it.set(p, null)
            }
        }
    }

    private fun ISubScript.initRoute(root: Route) {
        val routes = routes_key.run { get() } ?: return
        routes.forEach {
            val route = root.createRouteFromPath(it.path)
            it.inst = route
            it.body.invoke(route)
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
                routes.forEach {
                    it.inst?.removeSelf()
                }
            }
        }
    }
}

@ContextDsl
fun ISubScript.route(path: String, body: Route.() -> Unit) {
    routes.add(RouteHelper.RouteInfo(path, body, null))
}