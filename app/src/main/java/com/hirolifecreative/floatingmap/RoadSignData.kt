package com.hirolifecreative.floatingmap

import org.json.JSONObject

object RoadSignData {
    data class Sign(val id: Long,val lat: Double,val lon: Double,val kind: String)
    fun parse(body: JSONObject): List<Sign> {
        val elements = body.optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            if (e.optString("type")!="node") return@mapNotNull null
            val kind = e.optJSONObject("tags")?.optString("highway") ?: return@mapNotNull null
            if (kind !in listOf("traffic_signals","stop","give_way","crossing","speed_camera")) return@mapNotNull null
            val lat = e.optDouble("lat",Double.NaN);val lon = e.optDouble("lon",Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val id = e.optLong("id",-1)
            if (id<0) return@mapNotNull null
            Sign(id,lat,lon,kind)
        }.distinctBy { it.id }
    }
}
