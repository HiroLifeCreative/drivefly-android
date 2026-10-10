package com.hirolifecreative.floatingmap

/** Use the same forward tilt convention for the screen arrow and native model. */
object PuckOrientation {
    fun rotationX(tilt: Int): Float=tilt.coerceIn(0,60).toFloat()
    fun perspective(cameraPitch: Double,configuredPitch: Int): Float {
        if(!cameraPitch.isFinite() || cameraPitch<=0 || configuredPitch<=0) return 0f
        return (cameraPitch/configuredPitch.coerceIn(1,75)).coerceIn(0.0,1.0).toFloat()
    }
    fun viewTilt(tilt: Int,cameraPitch: Double,configuredPitch: Int): Float=
        rotationX(tilt)*perspective(cameraPitch,configuredPitch)
}
