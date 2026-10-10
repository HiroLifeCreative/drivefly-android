package com.hirolifecreative.floatingmap

/** Three qualifying fixes confirm arrival; jitter cannot restart the twenty-second clock. */
class ArrivalCountdown {
    private var confirmations = 0
    var deadline: Long? = null;private set
    fun observe(now: Long,eligible: Boolean,departed: Boolean) {
        if (departed) { reset();return }
        if (deadline != null) return
        confirmations = if (eligible) confirmations+1 else 0
        if (confirmations>=3) deadline = now+20000
    }
    fun due(now: Long) = deadline?.let { now>=it } ?: false
    fun secondsLeft(now: Long) = deadline?.let { ((it-now).coerceAtLeast(0)+999)/1000 }?.toInt()
    fun reset() { confirmations=0;deadline=null }
}
