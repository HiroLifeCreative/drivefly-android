package com.hirolifecreative.floatingmap

import kotlin.math.*

/** Projects onto road segments, not vertices. Requires sustained, accurate deviation. */
class RouteMonitor {
    data class Match(val distance: Double,val along: Double,val heading: Double,val total: Double)
    var maxProgress = 0.0;private set
    private var previousAlong: Double? = null
    private var deviations = 0
    private var deviationSince: Long? = null
    fun reset() { maxProgress = 0.0;previousAlong = null;deviations = 0;deviationSince = null }
    fun evaluate(points: List<RouteParser.Coordinate>,lon: Double,lat: Double,accuracy: Double,speed: Double,bearing: Double?,timeMillis: Long? = null): Pair<Match?,Boolean> {
        if (!accuracy.isFinite() || accuracy > 35 || accuracy < 0 || !lon.isFinite() || !lat.isFinite()) { deviations=0;deviationSince=null;return null to false }
        val match = match(points,lon,lat,previousAlong) ?: return null to false
        // A deviation must never consume the road or announce later turns.
        if (match.distance <= maxOf(25.0,accuracy*1.2)) {
            previousAlong = match.along
            maxProgress = maxOf(maxProgress,match.along)
        }
        val angle = bearing?.let { abs((it-match.heading+540)%360-180) } ?: 0.0
        val reversing = speed >= 2.5 && angle > 120 &&
            (maxProgress-match.along > 20 || match.along < 10 && match.distance > 15)
        val offRoad = match.distance > maxOf(45.0,accuracy*1.8)
        deviations = if (offRoad || reversing) deviations+1 else 0
        if (deviations == 0) deviationSince=null else if (deviationSince == null) deviationSince=timeMillis
        val sustained = timeMillis == null || deviationSince?.let { timeMillis-it >= 4000 } == true
        return match to (deviations >= 3 && sustained)
    }
    companion object {
        fun match(points: List<RouteParser.Coordinate>,lon: Double,lat: Double,reference: Double? = null): Match? {
            if (points.size < 2) return null
            val cosine = cos(Math.toRadians(lat));val factor = Math.PI/180*6371000
            var along = 0.0;var best: Match? = null
            for (i in 0 until points.lastIndex) {
                val a = points[i];val b = points[i+1]
                val ax = (a.longitude-lon)*factor*cosine;val ay = (a.latitude-lat)*factor
                val dx = (b.longitude-a.longitude)*factor*cosine;val dy = (b.latitude-a.latitude)*factor
                val length = hypot(dx,dy)
                if (length < .01) continue
                val fraction = (-(ax*dx+ay*dy)/(length*length)).coerceIn(0.0,1.0)
                val distance = hypot(ax+dx*fraction,ay+dy*fraction)
                val candidate = Match(distance,along+length*fraction,(Math.toDegrees(atan2(dx,dy))+360)%360,0.0)
                val prior = best
                if (prior == null || distance < prior.distance-1 || abs(distance-prior.distance) <= 1 && reference != null && abs(candidate.along-reference) < abs(prior.along-reference)) best = candidate
                along += length
            }
            return best?.copy(total=along)
        }
    }
}
