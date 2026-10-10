package com.hirolifecreative.floatingmap

import kotlin.math.abs
import kotlin.math.exp

/** Exact critically damped motion; retargeting preserves current position and velocity. */
class SmoothValue(initial: Double = 0.0,private val smoothTime: Double = .7) {
    var value = initial;private set
    var velocity = 0.0;private set
    fun reset(position: Double,speed: Double = 0.0) { value = position;velocity = speed }
    fun advance(target: Double,seconds: Double): Double {
        if (!target.isFinite() || !seconds.isFinite() || seconds <= 0) return value
        val omega = 2.0/smoothTime
        val offset = value-target
        val temp = (velocity+omega*offset)*seconds
        val decay = exp(-omega*seconds)
        value = target+(offset+temp)*decay
        velocity = (velocity-omega*temp)*decay
        return value
    }
    fun settled(target: Double,tolerance: Double): Boolean = abs(value-target) < tolerance && abs(velocity) < tolerance
    companion object {
        fun nearestAngle(current: Double,target: Double): Double = current+((target-current)%360+540)%360-180
    }
}
