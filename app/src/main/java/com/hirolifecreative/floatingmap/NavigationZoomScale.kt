package com.hirolifecreative.floatingmap

import org.json.JSONArray

/** Shared, bounded sizing for overlays and style expressions; smooth at every zoom stop. */
object NavigationZoomScale {
    val stops: List<Pair<Double,Double>> = listOf(10.0 to .4,14.0 to .6,16.0 to .8,CameraConfig.BASE_ZOOM to 1.0,19.0 to 1.4,22.0 to 1.7)
    fun factor(zoom: Double): Double {
        if(!zoom.isFinite()) return 1.0
        if(zoom<=stops.first().first) return stops.first().second
        for(i in 1 until stops.size) {
            val a=stops[i-1];val b=stops[i]
            if(zoom<=b.first) return a.second+(b.second-a.second)*(zoom-a.first)/(b.first-a.first)
        }
        return stops.last().second
    }
    fun modelExpression(base: Double): String=JSONArray().apply {
        put("interpolate");put(JSONArray().put("linear"));put(JSONArray().put("zoom"))
        stops.forEach { stop ->
            val value=base*stop.second
            put(stop.first);put(JSONArray().put("literal").put(JSONArray().put(value).put(value).put(value)))
        }
    }.toString()
    fun expression(base: Double): String=JSONArray().apply {
        put("interpolate");put(JSONArray().put("linear"));put(JSONArray().put("zoom"))
        stops.forEach { put(it.first);put(base*it.second) }
    }.toString()
}
