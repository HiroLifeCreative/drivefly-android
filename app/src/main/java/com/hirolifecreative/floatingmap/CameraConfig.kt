package com.hirolifecreative.floatingmap

import kotlin.math.ln

data class CameraConfig(val tilt: Int = 60,val distancePercent: Int = 100,
    val speedZoomEnabled: Boolean = false,val slowMph: Int = 15,val fastMph: Int = 60,
    val slowDistanceChange: Int = 20,val fastDistanceChange: Int = -20,val nativePuck3D: Boolean = false,val puckTilt: Int = 0,val puckSizePercent: Int = 100) {
    val zoom: Double get() = BASE_ZOOM-ln(distancePercent/100.0)/ln(2.0)
    fun normalized(): CameraConfig {
        val slow = slowMph.coerceIn(0,119)
        return copy(tilt=tilt.coerceIn(0,75),distancePercent=distancePercent.coerceIn(50,1000),
            puckTilt=puckTilt.coerceIn(0,60),puckSizePercent=puckSizePercent.coerceIn(50,250),slowMph=slow,fastMph=fastMph.coerceIn(slow+1,120),
            slowDistanceChange=slowDistanceChange.coerceIn(-80,200),fastDistanceChange=fastDistanceChange.coerceIn(-80,200))
    }
    companion object { const val BASE_ZOOM = 17.145 }
}

/** Percentages change visible distance relative to the user's saved base, not zoom levels. */
object SpeedZoom {
    fun offset(config: CameraConfig,mph: Double?): Double {
        if (!config.speedZoomEnabled || mph == null || !mph.isFinite() || mph<0) return 0.0
        val c = config.normalized()
        val percent = when { mph<=c.slowMph -> c.slowDistanceChange;mph>c.fastMph -> c.fastDistanceChange;else -> 0 }
        return -ln(1+percent/100.0)/ln(2.0)
    }
    fun target(config: CameraConfig,mph: Double?) = (config.zoom+offset(config,mph)).coerceIn(10.0,22.0)
}
