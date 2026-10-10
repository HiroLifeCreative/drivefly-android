package com.hirolifecreative.floatingmap

import android.content.Context
import androidx.core.util.AtomicFile
import org.json.JSONObject

data class VoiceConfig(val mode: String = "claude",val speedPercent: Int = 100,val orderAnnouncements: Boolean=true,val arrivalDetails: Boolean=true) {
    fun normalized() = copy(mode=mode.takeIf { it in listOf("claude","android","off") } ?: "claude",speedPercent=speedPercent.coerceIn(80,120))
}
object VoicePreferences {
    fun decode(json: JSONObject) = VoiceConfig(json.optString("mode","claude"),json.optInt("speedPercent",100),json.optBoolean("orderAnnouncements",true),json.optBoolean("arrivalDetails",true)).normalized()
    fun encode(config: VoiceConfig): JSONObject = config.normalized().let { JSONObject().put("mode",it.mode).put("speedPercent",it.speedPercent).put("orderAnnouncements",it.orderAnnouncements).put("arrivalDetails",it.arrivalDetails) }
    fun read(context: Context): VoiceConfig = try {
        decode(JSONObject(String(AtomicFile(java.io.File(context.filesDir,"voice-preferences.json")).readFully(),Charsets.UTF_8)))
    } catch (_: Exception) { VoiceConfig() }
    fun save(context: Context,config: VoiceConfig) {
        val file=AtomicFile(java.io.File(context.filesDir,"voice-preferences.json"));val output=file.startWrite()
        try { output.write(encode(config).toString().toByteArray(Charsets.UTF_8));file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output);throw e }
        context.sendBroadcast(android.content.Intent(ACTION).setPackage(context.packageName))
    }
    const val ACTION="com.hirolifecreative.floatingmap.VOICE_SETTINGS"
}

/** Distance-based cues, emitted once per approach stage by the shared GPS session. */
class VoiceCueTracker {
    private var key: Pair<String,Int>? = null
    private var stage = -1
    private var lastSpoken = Long.MIN_VALUE
    fun reset() { key=null;stage=-1;lastSpoken=Long.MIN_VALUE }
    fun maneuver(route: String,step: Int,instruction: String,road: String,
                 meters: Double,speed: Double,now: Long): String? {
        if (instruction.isBlank() || step<0 || !meters.isFinite() || meters < -3) return null
        val next=route to step
        if (key!=next) { key=next;stage=-1 }
        val immediate=(speed.takeIf { it.isFinite() && it>=0 } ?: 0.0).times(4).coerceIn(25.0,80.0)
        val phase=when { meters<=immediate -> 3;meters<=200 -> 2;meters<=500 -> 1;else -> 0 }
        if (phase<=stage) return null
        // Avoid back-to-back previews after a turn. The near-turn cue has priority.
        if (phase!=3 && lastSpoken!=Long.MIN_VALUE && now-lastSpoken<8000) return null
        stage=phase;lastSpoken=now
        val action=instruction.trim().trimEnd('.')
        val street=road.trim().takeIf { it.isNotBlank() && !action.contains(it,true) }
        val maneuver=action+(street?.let { ", hacia $it" } ?: "")
        if (phase==3) return "$maneuver."
        val distance=if(meters>=1609.344) {
            val miles=meters/1609.344
            if(miles<1.15) "una milla" else String.format(java.util.Locale.US,"%.1f",miles).replace('.',',')+" millas"
        } else {
            val rounded=(kotlin.math.round(meters/50)*50).toInt().coerceAtLeast(50)
            "$rounded metros"
        }
        return if(phase==0) "Continúa durante $distance. Después, ${maneuver.replaceFirstChar { it.lowercase() }}."
            else "En $distance, ${maneuver.replaceFirstChar { it.lowercase() }}."
    }
}
