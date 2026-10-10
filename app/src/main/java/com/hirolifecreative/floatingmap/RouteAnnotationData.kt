package com.hirolifecreative.floatingmap
import org.json.JSONObject
import kotlin.math.*

/** Mapbox segment annotations; missing coverage never becomes an invented speed limit. */
data class RouteAnnotationData(val limitsMph: List<Double?> = emptyList(),val meanCongestion: Double? = null) {
    companion object {
        fun parse(route: JSONObject,pointCount: Int): RouteAnnotationData {
            val limits=mutableListOf<Double?>();var knownDistance=0.0;var totalDistance=0.0;var weighted=0.0;var fullCoverage=true;var segmentCount=0
            val legs=route.optJSONArray("legs") ?: return RouteAnnotationData()
            for(i in 0 until legs.length()) {
                val annotation=legs.optJSONObject(i)?.optJSONObject("annotation")
                if(annotation==null) { fullCoverage=false;continue }
                val max=annotation.optJSONArray("maxspeed")
                if(max!=null) for(j in 0 until max.length()) {
                    val item=max.optJSONObject(j)
                    val speed=item?.optDouble("speed",Double.NaN) ?: Double.NaN
                    limits+=if(item!=null&&!item.optBoolean("unknown")&&!item.optBoolean("none")&&speed.isFinite()&&speed>0) when(item.optString("unit")) { "mph"->speed;"km/h"->speed/1.609344;else->null } else null
                }
                val distances=annotation.optJSONArray("distance");val congestion=annotation.optJSONArray("congestion_numeric")
                if(distances==null) fullCoverage=false else segmentCount+=distances.length()
                if(distances!=null) for(j in 0 until distances.length()) {
                    val meters=distances.optDouble(j,0.0);if(!meters.isFinite()||meters<0) continue
                    totalDistance+=meters
                    val value=congestion?.optDouble(j,Double.NaN) ?: Double.NaN
                    if(value.isFinite()&&value in 0.0..100.0) { knownDistance+=meters;weighted+=meters*value }
                }
            }
            return RouteAnnotationData(if(limits.size==pointCount-1) limits else emptyList(),if(fullCoverage&&segmentCount==pointCount-1&&totalDistance>0&&knownDistance/totalDistance>=.8) weighted/knownDistance else null)
        }
        fun limit(result: RoutingClient.Result?,lon: Double,lat: Double): Double? {
            val route=result?.route ?: return null;val limits=result.annotation.limitsMph
            if(limits.size!=route.coordinates.size-1) return null
            val match=RouteMonitor.match(route.coordinates,lon,lat) ?: return null
            if(match.distance>35) return null
            var meters=0.0
            for(i in 0 until route.coordinates.lastIndex) {
                val a=route.coordinates[i];val b=route.coordinates[i+1];val factor=PI/180*6371000
                val length=hypot((b.longitude-a.longitude)*factor*cos(Math.toRadians((a.latitude+b.latitude)/2)),(b.latitude-a.latitude)*factor)
                if(match.along<meters+length||i==limits.lastIndex) return limits.getOrNull(i)
                meters+=length
            }
            return null
        }
    }
}
