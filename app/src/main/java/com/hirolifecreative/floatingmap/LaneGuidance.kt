package com.hirolifecreative.floatingmap

import org.json.JSONObject

object LaneGuidance {
    data class Lane(val directions: List<String>,val valid: Boolean,val active: Boolean,val routeDirection: String?)
    private val directions=setOf("straight","sharp left","left","slight left","slight right","right","sharp right","uturn")
    fun parse(step: JSONObject,location: RouteParser.Coordinate): List<Lane> {
        val intersections=step.optJSONArray("intersections") ?: return emptyList()
        val relevant=(0 until intersections.length()).mapNotNull { intersections.optJSONObject(it) }.filter {
            val xy=it.optJSONArray("location") ?: return@filter false
            val lon=xy.optDouble(0,Double.NaN);val lat=xy.optDouble(1,Double.NaN)
            lon.isFinite()&&lat.isFinite()&&DestinationAccess.distance(DestinationAccess.Point(lat,lon),DestinationAccess.Point(location.latitude,location.longitude))<=20
        }
        if(relevant.size!=1) return emptyList()
        val array=relevant.single().optJSONArray("lanes") ?: return emptyList()
        if(array.length() !in 1..10) return emptyList()
        val lanes=(0 until array.length()).map { i ->
            val lane=array.optJSONObject(i) ?: return emptyList()
            if(!lane.has("valid") || lane.opt("valid") !is Boolean) return emptyList()
            val indications=lane.optJSONArray("indications") ?: return emptyList()
            val allowed=(0 until indications.length()).map { indications.optString(it) }.filter { it in directions }.distinct()
            val valid=lane.optBoolean("valid",false)
            val routeDirection=lane.optString("valid_indication").takeIf { valid&&it in allowed }
            Lane(allowed,valid,valid&&lane.optBoolean("active",false),routeDirection)
        }
        return lanes.takeIf { it.any { lane -> lane.valid&&lane.directions.isNotEmpty() } } ?: emptyList()
    }
    fun recommended(lanes: List<Lane>,index: Int): Boolean {
        val lane=lanes.getOrNull(index) ?: return false
        return if(lanes.any { it.active }) lane.active else lane.valid
    }
}
