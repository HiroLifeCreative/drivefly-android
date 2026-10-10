package com.hirolifecreative.floatingmap

import android.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView

/** One camera policy for both screen sizes; padding scales to the actual viewport. */
object NavigationCameraProfile {
    const val ACTIVE_PITCH = 60.0
    // An additional 50% visible-distance increase: 17.73 - log2(1.5).
    const val ACTIVE_ZOOM = 17.145
    fun options(map: MapView,fix: Location,active: Boolean,threeD: Boolean = NavigationSession.threeD,bearing: Double = if (active) DriveFlyMap.renderedBearing(map) ?: NavigationSession.bearing else NavigationSession.bearing): CameraOptions {
        val config = CameraPreferences.current(map.context)
        val height = map.height.coerceAtLeast(1).toDouble()
        val position = if (active) DriveFlyMap.renderedPoint(map) ?: Point.fromLngLat(fix.longitude,fix.latitude) else Point.fromLngLat(fix.longitude,fix.latitude)
        return CameraOptions.Builder().center(position)
            .zoom(if (active) SpeedZoom.target(config,SpeedMotion.mph) else SpeedZoom.target(config,SpeedMotion.mph)-0.645)
            .pitch(if (!threeD) 0.0 else config.tilt.toDouble())
            .bearing(bearing)
            .padding(if (active) EdgeInsets(height*NavigationScreenAnchor.TOP_PADDING,0.0,height*NavigationScreenAnchor.BOTTOM_PADDING,0.0) else EdgeInsets(0.0,0.0,0.0,0.0))
            .build()
    }
}
