package com.hirolifecreative.floatingmap

import kotlin.math.abs

/** Display-only road projection. Raw GPS remains untouched for off-route detection. */
class RouteVisualTracker {
    data class Position(val coordinate: RouteParser.Coordinate,val heading: Double,val along: Double?,val snapped: Boolean)
    private var previous: Position? = null
    fun reset() { previous = null }
    fun update(points: List<RouteParser.Coordinate>,lon: Double,lat: Double,accuracy: Double,speed: Double,bearing: Double?): Position? {
        if (!accuracy.isFinite() || accuracy > 35 || accuracy < 0) return previous
        val match = RouteMonitor.match(points,lon,lat,previous?.along) ?: return null
        if (match.distance > 45) {
            return Position(RouteParser.Coordinate(lon,lat),bearing ?: previous?.heading ?: match.heading,null,false).also { previous = it }
        }
        val old = previous
        // GPS wander at a stop must not pan or rotate the navigation view.
        if (speed < 1.2 && old?.snapped == true && old.along != null && abs(match.along-old.along) < 12) return old
        val angle = bearing?.let { abs((it-match.heading+540)%360-180) } ?: 0.0
        if (old?.snapped == true && old.along != null && angle <= 120 && old.along-match.along in 0.0..12.0) return old
        val heading = if (speed >= 2.5 && angle > 120) bearing!! else match.heading
        return Position(RouteLineGeometry.pointAt(points,match.along) ?: points.last(),heading,match.along,true).also { previous = it }
    }
}
