package com.hirolifecreative.floatingmap

import kotlin.math.*

/** Cuts exactly inside a segment, retaining only the route ahead of the vehicle. */
object RouteLineGeometry {
    fun pointAt(points: List<RouteParser.Coordinate>,meters: Double): RouteParser.Coordinate? {
        if (points.isEmpty()) return null
        if (meters <= 0) return points.first()
        var consumed = 0.0
        for (i in 0 until points.lastIndex) {
            val a = points[i];val b = points[i+1]
            val factor = Math.PI/180*6371000
            val length = hypot((b.longitude-a.longitude)*factor*cos(Math.toRadians((a.latitude+b.latitude)/2)),(b.latitude-a.latitude)*factor)
            if (length < .01) continue
            if (consumed+length > meters) {
                val t = ((meters-consumed)/length).coerceIn(0.0,1.0)
                return RouteParser.Coordinate(a.longitude+(b.longitude-a.longitude)*t,a.latitude+(b.latitude-a.latitude)*t)
            }
            consumed += length
        }
        return points.last()
    }
    fun remaining(points: List<RouteParser.Coordinate>,meters: Double): List<RouteParser.Coordinate> {
        if (points.size < 2 || meters <= 0) return points
        var consumed = 0.0
        for (i in 0 until points.lastIndex) {
            val a = points[i];val b = points[i+1]
            val factor = Math.PI/180*6371000
            val length = hypot((b.longitude-a.longitude)*factor*cos(Math.toRadians((a.latitude+b.latitude)/2)),(b.latitude-a.latitude)*factor)
            if (length < .01) continue
            if (consumed+length > meters) {
                val fraction = ((meters-consumed)/length).coerceIn(0.0,1.0)
                val head = RouteParser.Coordinate(a.longitude+(b.longitude-a.longitude)*fraction,a.latitude+(b.latitude-a.latitude)*fraction)
                return listOf(head)+points.drop(i+1)
            }
            consumed += length
        }
        return emptyList()
    }
}
