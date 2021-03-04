package ktor.lib.util

import io.ktor.routing.*

/**
 * 用于辅助routing热重载
 * 使用[collect]搜集route的handler
 * 使用[minus]生成handler对比
 * 使用[tryRemoveAll]从route上移除所有handler
 * note: 不支持intercept
 */
class RouteHandlerList {
    companion object {
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
    }

    val map = mutableMapOf<Route, Set<Any>>()
    fun collect(route: Route): RouteHandlerList {
        route.handlers.let {
            if (it.isNotEmpty())
                map[route] = it.toSet()
        }
        route.children.forEach(::collect)
        return this
    }

    operator fun minus(old: RouteHandlerList): RouteHandlerList {
        val new = RouteHandlerList()
        map.forEach { (k, v) ->
            val set = v - old.map[k].orEmpty()
            if (set.isNotEmpty())
                new.map[k] = set
        }
        return new
    }

    fun tryRemoveAll() {
        map.forEach { (route, v) ->
            val handlers = route.handlers
            handlers.removeAll(v)
            if (handlers.isEmpty() && route.children.isEmpty())
                route.removeSelf()
            else
                route.removeCache()
        }
    }
}