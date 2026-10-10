package com.hirolifecreative.floatingmap

import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

/** An entrance locates a door; the default routable point locates vehicle access. */
object DestinationAccess {
    data class Point(val lat: Double,val lon: Double)
    data class Access(val door: Point,val vehicle: Point,val hasEntrance: Boolean)
    private fun point(json: JSONObject): Point? {
        val coordinates=json.optJSONArray("coordinates")
        val lat=if(coordinates!=null) coordinates.optDouble(1,Double.NaN) else json.optDouble("latitude",Double.NaN)
        val lon=if(coordinates!=null) coordinates.optDouble(0,Double.NaN) else json.optDouble("longitude",Double.NaN)
        return Point(lat,lon).takeIf { lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0 }
    }
    fun distance(a: Point,b: Point): Double=6371000*hypot(Math.toRadians(a.lat-b.lat),Math.toRadians(a.lon-b.lon)*cos(Math.toRadians((a.lat+b.lat)/2)))
    fun parse(feature: JSONObject,center: Point,allowEntrance: Boolean=true): Access {
        val properties=feature.optJSONObject("properties")
        val raw=feature.opt("routable_points") ?: properties?.opt("routable_points") ?: properties?.optJSONObject("coordinates")?.opt("routable_points")
        val array=when(raw) { is JSONArray -> raw;is JSONObject -> raw.optJSONArray("points");else -> null } ?: return Access(center,center,false)
        val points=(0 until array.length()).mapNotNull { index ->
            val item=array.optJSONObject(index) ?: return@mapNotNull null
            val p=point(item)?.takeIf { distance(it,center)<=300 } ?: return@mapNotNull null
            item to p
        }
        fun unique(candidates: List<Pair<JSONObject,Point>>,spread: Double): Point? {
            val first=candidates.firstOrNull()?.second ?: return null
            return first.takeIf { candidates.all { distance(first,it.second)<=spread } }
        }
        val vehicle=unique(points.filter { it.first.optString("name")=="default" },20.0) ?: center
        val door=if(allowEntrance) unique(points.filter { it.first.optString("name")=="entrance" && it.first.optString("accuracy")=="high" },10.0) else null
        return Access(door ?: center,vehicle,door!=null)
    }
}
