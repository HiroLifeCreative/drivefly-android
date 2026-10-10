package com.hirolifecreative.floatingmap

import kotlin.math.PI
import kotlin.math.cos

/** Same units as the speed readout; missing or invalid limits never imply speeding. */
object SpeedWarning {
    fun active(mph: Double?,limit: Double?): Boolean = mph!=null && limit!=null &&
        mph.isFinite() && limit.isFinite() && mph>=0 && limit>0 && mph>limit
    fun pulse(elapsedMillis: Long): Float = (.65+.35*cos(2*PI*(elapsedMillis%1200)/1200.0)).toFloat()
}
