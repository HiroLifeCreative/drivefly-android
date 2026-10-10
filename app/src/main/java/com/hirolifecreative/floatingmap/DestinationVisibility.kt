package com.hirolifecreative.floatingmap

object DestinationVisibility {
    const val APPEAR_METERS=1609.344
    const val FULL_METERS=500.0
    fun alpha(meters: Double?): Float {
        if (meters==null || !meters.isFinite() || meters<0) return 0f
        val t=((APPEAR_METERS-meters)/(APPEAR_METERS-FULL_METERS)).coerceIn(0.0,1.0)
        return (t*t*(3-2*t)).toFloat()
    }
}
