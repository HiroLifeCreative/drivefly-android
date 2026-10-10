package com.hirolifecreative.floatingmap

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/** One animated speed feeds both camera targets and both speed readouts. */
object SpeedMotion {
    private val handler=Handler(Looper.getMainLooper())
    private val frames=Choreographer.getInstance()
    private val motion=SmoothValue(smoothTime=.8)
    private val listeners=linkedSetOf<(Double?) -> Unit>()
    private var target: Double?=null
    private var scheduled=false
    private var lastFrame=0L
    val mph: Double? get()=if(target==null) null else motion.value.coerceAtLeast(0.0)
    private fun emit() { val speed=mph;listeners.toList().forEach { it(speed) } }
    private val stale=Runnable { set(null) }
    private val frame=Choreographer.FrameCallback { time -> advance(time) }
    fun observe(listener: (Double?) -> Unit) { listeners+=listener;listener(mph) }
    fun remove(listener: (Double?) -> Unit) { listeners-=listener }
    fun set(value: Double?) {
        if(Looper.myLooper()!=Looper.getMainLooper()) { handler.post { set(value) };return }
        handler.removeCallbacks(stale)
        val valid=value?.takeIf { it.isFinite() && it in 0.0..200.0 }
        if(valid==null) {
            target=null;frames.removeFrameCallback(frame);scheduled=false;lastFrame=0;emit();return
        }
        if(target==null) motion.reset(valid)
        target=valid
        handler.postDelayed(stale,15000)
        if(!scheduled) { scheduled=true;frames.postFrameCallback(frame) }
    }
    private fun advance(time: Long) {
        val goal=target ?: run { scheduled=false;return }
        val seconds=if(lastFrame==0L) 1.0/60 else ((time-lastFrame)/1e9).coerceIn(.001,.05)
        lastFrame=time;motion.advance(goal,seconds);emit()
        if(motion.settled(goal,.005)) { motion.reset(goal);emit();scheduled=false;lastFrame=0 }
        else frames.postFrameCallback(frame)
    }
}
