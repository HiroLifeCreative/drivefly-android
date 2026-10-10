package com.hirolifecreative.floatingmap

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class CrosswalkDataTest {
    private fun way(tags: String="\"crossing:markings\":\"zebra\"",end: Double=.00009)=JSONObject("""{"elements":[{"type":"way","id":10,"tags":{"highway":"footway","footway":"crossing",$tags},"nodes":[1,2],"geometry":[{"lat":0,"lon":0},{"lat":$end,"lon":0}]}]}""")
    @Test fun markedGeometryProducesClosedGroundStripes() {
        val crossing=CrosswalkData.parse(way()).single()
        val stripes=CrosswalkData.stripes(crossing)
        assertTrue(stripes.size>=10)
        for(ring in stripes) {
            assertEquals(5,ring.size);assertEquals(ring.first(),ring.last())
            assertEquals(3.0,CrosswalkData.distance(ring[0],ring[1]),.01)
            assertEquals(.45,CrosswalkData.distance(ring[1],ring[2]),.01)
            assertTrue(ring.all { it.lat>0&&it.lat<.00009 })
        }
    }
    @Test fun unmarkedOrUnknownNeverInventsPaint() {
        assertTrue(CrosswalkData.parse(way("\"crossing\":\"unmarked\"")).isEmpty())
        assertTrue(CrosswalkData.parse(way("\"crossing\":\"traffic_signals\"")).isEmpty())
        assertTrue(CrosswalkData.parse(way("\"crossing\":\"marked\",\"crossing:markings\":\"no\"")).isEmpty())
    }
    @Test fun excessiveExtentAndInvalidGeometryAreRejected() {
        assertTrue(CrosswalkData.parse(way(end=.001)).isEmpty())
        assertTrue(CrosswalkData.parse(way(end=91.0)).isEmpty())
        assertTrue(CrosswalkData.parse(way(end=.000001)).isEmpty())
    }
    private fun nodeRoad(width: String="\"width\":\"8\",",extra: String="")=JSONObject("""{"elements":[
       {"type":"node","id":2,"lat":0,"lon":0,"tags":{"highway":"crossing","crossing:markings":"zebra"}},
       {"type":"way","id":20,"tags":{$width"highway":"residential"},"nodes":[1,2,3],"geometry":[{"lat":0,"lon":-0.0001},{"lat":0,"lon":0},{"lat":0,"lon":0.0001}]}$extra]}""")
    @Test fun nodeRequiresKnownRoadWidthAndStripesRunAcrossRoad() {
        assertTrue(CrosswalkData.parse(nodeRoad(width="")).isEmpty())
        val crossing=CrosswalkData.parse(nodeRoad()).single()
        assertEquals(8.0,CrosswalkData.distance(crossing.path[0],crossing.path[1]),.01)
        assertEquals(0.0,crossing.path[0].lon,.0000001)
        assertTrue(CrosswalkData.stripes(crossing).all { ring -> absLongitude(ring[0])>0 })
    }
    private fun absLongitude(c: CrosswalkData.Coordinate)=kotlin.math.abs(c.lon)
    @Test fun ambiguousIntersectionIsOmitted() {
        val extra=""",{"type":"way","id":21,"tags":{"width":"6","highway":"service"},"nodes":[4,2,5],"geometry":[{"lat":-0.0001,"lon":0},{"lat":0,"lon":0},{"lat":0.0001,"lon":0}]}"""
        assertTrue(CrosswalkData.parse(nodeRoad(extra=extra)).isEmpty())
    }
    @Test fun explicitWidthRespectedAndNonEquatorialCoordinatesStayMetric() {
        val crossing=CrosswalkData.parse(way("\"crossing\":\"marked\",\"width\":\"4 m\"")).single()
        assertEquals(4.0,crossing.width,0.0)
        val shifted=crossing.copy(path=crossing.path.map { CrosswalkData.Coordinate(it.lon-81.3,it.lat+29.8) })
        val ring=CrosswalkData.stripes(shifted).first()
        assertEquals(4.0,CrosswalkData.distance(ring[0],ring[1]),.01)
        assertEquals(.45,CrosswalkData.distance(ring[1],ring[2]),.01)
    }
}
