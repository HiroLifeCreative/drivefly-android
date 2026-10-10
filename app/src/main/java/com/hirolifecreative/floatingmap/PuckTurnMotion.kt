package com.hirolifecreative.floatingmap

/** Small visual response to an actual heading change; never changes the fixed location anchor. */
object PuckTurnMotion {
    fun target(cameraBearing: Double,heading: Double,mph: Double?): Double {
        if (!cameraBearing.isFinite() || !heading.isFinite() || mph == null || !mph.isFinite() || mph < 3) return 0.0
        val delta=SmoothValue.nearestAngle(cameraBearing,heading)-cameraBearing
        if (kotlin.math.abs(delta)<3) return 0.0
        return (delta*.45).coerceIn(-14.0,14.0)
    }
}
