package com.hirolifecreative.floatingmap

import org.json.JSONObject
import kotlin.math.*

/** Ground geometry, not screen icons. Unknown/unmarked crossings never become zebra paint. */
object CrosswalkData {
    data class Coordinate(val lon: Double,val lat: Double)
    data class Crossing(val id: Long,val path: List<Coordinate>,val width: Double)
    private const val R=6371000.0
    private fun coordinate(e: JSONObject): Coordinate? {
        val lat=e.optDouble("lat",Double.NaN);val lon=e.optDouble("lon",Double.NaN)
        return if(lat.isFinite()&&lon.isFinite()&&lat in -85.0..85.0&&lon in -180.0..180.0) Coordinate(lon,lat) else null
    }
    private fun marked(tags: JSONObject?): Boolean {
        if(tags==null) return false
        val markings=tags.optString("crossing:markings").lowercase()
        if(markings in listOf("no","none")) return false
        if(tags.optString("crossing") in listOf("unmarked","no")) return false
        return markings in listOf("zebra","zebra:double","yes","ladder") || tags.optString("crossing") in listOf("zebra","marked")
    }
    private fun width(tags: JSONObject?,default: Double?=null): Double? {
        val raw=tags?.optString("width")?.trim()?.removeSuffix(" m")?.toDoubleOrNull()
        return (raw ?: default)?.takeIf { it in 1.0..45.0 }
    }
    fun parse(body: JSONObject): List<Crossing> {
        val elements=body.optJSONArray("elements") ?: return emptyList()
        val nodes=(0 until elements.length()).mapNotNull { elements.optJSONObject(it) }.filter { it.optString("type")=="node" }.associateBy { it.optLong("id",-1) }
        val ways=(0 until elements.length()).mapNotNull { elements.optJSONObject(it) }.filter { it.optString("type")=="way" }
        val result=mutableListOf<Crossing>();val covered=mutableSetOf<Long>()
        for(way in ways) {
            val tags=way.optJSONObject("tags") ?: continue
            if(tags.optString("footway")!="crossing") continue
            val ids=way.optJSONArray("nodes") ?: continue
            val crossingNodes=(0 until ids.length()).mapNotNull { nodes[ids.optLong(it)] }.filter { it.optJSONObject("tags")?.optString("highway")=="crossing" }
            if(!marked(tags)&&crossingNodes.none { marked(it.optJSONObject("tags")) }) continue
            if(tags.optString("crossing") in listOf("unmarked","no") || tags.optString("crossing:markings") in listOf("no","none")) continue
            val geometry=way.optJSONArray("geometry") ?: continue
            val points=(0 until geometry.length()).mapNotNull { geometry.optJSONObject(it)?.let(::coordinate) }
            if(points.size!=geometry.length() || points.size<2) continue
            val length=points.zipWithNext().sumOf { (a,b)->distance(a,b) }
            // Reject long paths spanning multiple roads: their painted extent is unknown.
            if(length !in 3.0..45.0) continue
            val stripWidth=width(tags,3.0)?.takeIf { it<=8.0 } ?: continue
            result+=Crossing(way.optLong("id"),points,stripWidth)
            crossingNodes.forEach { covered+=it.optLong("id") }
        }
        // A point alone is insufficient. A road's explicit width and its local direction
        // let us place a perpendicular crossing without inventing the road's dimensions.
        for((id,node) in nodes) {
            val tags=node.optJSONObject("tags") ?: continue
            if(id in covered || tags.optString("highway")!="crossing" || !marked(tags)) continue
            val point=coordinate(node) ?: continue
            val candidates=ways.filter { way ->
                val t=way.optJSONObject("tags");val highway=t?.optString("highway")
                highway in listOf("residential","service","tertiary","secondary","primary","unclassified","living_street") && width(t)!=null &&
                    way.optJSONArray("nodes")?.let { a -> (0 until a.length()).any { a.optLong(it)==id } }==true
            }
            if(candidates.size!=1) continue // junction ambiguity
            val way=candidates.single();val ids=way.getJSONArray("nodes");val geometry=way.optJSONArray("geometry") ?: continue
            if(ids.length()!=geometry.length()) continue
            val index=(0 until ids.length()).first { ids.optLong(it)==id }
            if(index==0 || index==ids.length()-1) continue
            val before=geometry.optJSONObject(index-1)?.let(::coordinate) ?: continue
            val after=geometry.optJSONObject(index+1)?.let(::coordinate) ?: continue
            if(distance(before,point)<1 || distance(after,point)<1) continue
            val incoming=bearing(before,point);val outgoing=bearing(point,after)
            if(abs(((outgoing-incoming+540)%360)-180)>30) continue
            val across=bearing(before,after)+90;val roadWidth=width(way.optJSONObject("tags")) ?: continue
            val stripWidth=width(tags,3.0)?.takeIf { it<=8 } ?: continue
            result+=Crossing(id,listOf(offset(point,across,roadWidth/2),offset(point,across+180,roadWidth/2)),stripWidth)
        }
        return result.distinctBy { it.path }.take(300)
    }
    fun surface(crossing: Crossing): List<List<Coordinate>> = crossing.path.zipWithNext().map { (a,b) ->
        val heading=bearing(a,b)
        val ring=listOf(offset(a,heading+90,crossing.width/2),offset(a,heading-90,crossing.width/2),offset(b,heading-90,crossing.width/2),offset(b,heading+90,crossing.width/2))
        ring+ring.first()
    }
    fun stripes(crossing: Crossing): List<List<Coordinate>> {
        val segments=crossing.path.zipWithNext();val lengths=segments.map { distance(it.first,it.second) };val total=lengths.sum()
        if(total !in 3.0..45.0 || crossing.width !in 1.0..8.0) return emptyList()
        val count=floor(total/.9).toInt().coerceAtMost(50)
        val margin=(total-(count-1)*.9)/2
        return (0 until count).mapNotNull { i ->
            var along=margin+i*.9;var segment=0
            while(segment<lengths.lastIndex && along>lengths[segment]) { along-=lengths[segment];segment++ }
            val (a,b)=segments[segment];val heading=bearing(a,b)
            if(lengths[segment]<.45 || along<.225 || along>lengths[segment]-.225) return@mapNotNull null
            val center=offset(a,heading,along);val front=offset(center,heading,.225);val back=offset(center,heading+180,.225)
            val ring=listOf(offset(front,heading+90,crossing.width/2),offset(front,heading-90,crossing.width/2),offset(back,heading-90,crossing.width/2),offset(back,heading+90,crossing.width/2))
            ring+ring.first()
        }
    }
    fun distance(a: Coordinate,b: Coordinate): Double {
        val dy=Math.toRadians(b.lat-a.lat);val dx=Math.toRadians(b.lon-a.lon)*cos(Math.toRadians((a.lat+b.lat)/2))
        return hypot(dx,dy)*R
    }
    private fun bearing(a: Coordinate,b: Coordinate)=Math.toDegrees(atan2(Math.toRadians(b.lon-a.lon)*cos(Math.toRadians((a.lat+b.lat)/2)),Math.toRadians(b.lat-a.lat)))
    private fun offset(p: Coordinate,bearing: Double,meters: Double): Coordinate {
        val angle=Math.toRadians(bearing)
        return Coordinate(p.lon+Math.toDegrees(sin(angle)*meters/R/cos(Math.toRadians(p.lat))),p.lat+Math.toDegrees(cos(angle)*meters/R))
    }
}
