package com.hirolifecreative.floatingmap

/** Keep the current maneuver until its intersection has actually been passed. */
object ManeuverProgress {
    fun next(steps: List<RouteParser.Step>,along: Double): Int {
        if (steps.size < 2) return 0
        var boundary=0.0
        var next=1
        for (i in 1 until steps.lastIndex) {
            boundary += steps[i-1].distance.takeIf { it.isFinite() && it >= 0 } ?: 0.0
            if (along >= boundary+12.0) next=i+1 else break
        }
        return next
    }
}
