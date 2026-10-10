package com.hirolifecreative.floatingmap

/** Exact offer actions only. No screen text is retained or logged. */
object DasherOfferPolicy {
    fun isAccept(text: String): Boolean = text.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+")," ").matches(Regex("(?:aceptar(?: oferta| pedido)?|accept(?: offer| order)?)(?:\\s*\\(?\\d{1,3}\\)?(?:\\s*s)?)?"))
    fun isOffer(texts: List<String>): Boolean {
        val lines=texts.flatMap { it.lines() }.map { it.trim() }
        if(lines.none(::isAccept)) return false
        val decline=lines.any { it.equals("rechazar",true)||it.equals("decline",true)||it.equals("decline offer",true)||it.equals("rechazar oferta",true) }
        val all=lines.joinToString(" ").lowercase(java.util.Locale.ROOT)
        val pay=Regex("(?:\\$\\s*\\d|\\d[\\d.,]*\\s*(?:us\\$|usd))").containsMatchIn(all)
        val distance=Regex("\\b\\d+(?:[.,]\\d+)?\\s*(?:mi|miles|millas|km)\\b").containsMatchIn(all)
        return decline || (pay&&distance)
    }
}

/** At most three failed click attempts; one successful click until the offer disappears. */
class DasherOfferLatch {
    private var attempts=0
    private var clicked=false
    private var nextAttempt=0L
    private var absentSince: Long?=null
    fun mayClick(now: Long,enabled: Boolean,offerVisible: Boolean): Boolean {
        if(!enabled) { reset();return false }
        if(!offerVisible) {
            val since=absentSince ?: now.also { absentSince=it }
            if(now-since>=4000) reset()
            return false
        }
        absentSince=null
        return !clicked&&attempts<3&&now>=nextAttempt
    }
    fun recordClick(now: Long,success: Boolean) { attempts++;clicked=success;nextAttempt=now+3000 }
    fun reset() { attempts=0;clicked=false;nextAttempt=0L;absentSince=null }
}
