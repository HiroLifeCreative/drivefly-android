package com.hirolifecreative.floatingmap

/** Visual maneuver notices use distance along the selected route, not viewport visibility. */
object ManeuverNotice {
    const val METERS=5.0
    fun visible(turnAlong: Double,fixAlong: Double,offRoute: Double,accuracy: Double,ageMillis: Long): Boolean {
        if(!turnAlong.isFinite() || !fixAlong.isFinite() || !offRoute.isFinite() || !accuracy.isFinite()) return false
        if(accuracy !in 0.0..25.0 || ageMillis !in 0L..15000L || offRoute<0 || offRoute>maxOf(10.0,accuracy)) return false
        return turnAlong-fixAlong in -METERS..METERS
    }
}
