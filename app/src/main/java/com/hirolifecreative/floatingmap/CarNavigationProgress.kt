package com.hirolifecreative.floatingmap

/** The car displays along-route distances from the same progress used by phone navigation. */
object CarNavigationProgress {
    fun metersToStep(steps: List<RouteParser.Step>,index: Int,remaining: Double?): Double {
        val total=steps.sumOf { it.distance.takeIf { d -> d.isFinite() && d>=0 } ?: 0.0 }
        val boundary=steps.take(index.coerceAtLeast(0)).sumOf { it.distance.takeIf { d -> d.isFinite() && d>=0 } ?: 0.0 }
        val consumed=remaining?.takeIf { it.isFinite() }?.let { total-it.coerceIn(0.0,total) } ?: 0.0
        return (boundary-consumed).coerceAtLeast(0.0)
    }
}
