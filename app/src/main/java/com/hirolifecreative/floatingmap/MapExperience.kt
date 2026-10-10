package com.hirolifecreative.floatingmap

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

data class MapExperienceConfig(
    val enabled: Boolean=true,
    val buildings: Boolean=true,
    val trees: Boolean=true,
    val landmarks: Boolean=true,
    val facades: Boolean=true,
    val detailedRoads: Boolean=true,
    val laneGuidance: Boolean=true,
    val entrances: Boolean=true,
    val lighting: String="theme",
    val weather: Boolean=true
) {
    fun lightPreset(dark: Boolean,hour: Int): String=when(lighting) {
        "clock" -> when(hour.coerceIn(0,23)) { in 6..7 -> "dawn";in 8..17 -> "day";in 18..19 -> "dusk";else -> "night" }
        "dawn","day","dusk","night" -> lighting
        else -> if(dark) "night" else "day"
    }
    fun json()=JSONObject().apply {
        put("enabled",enabled);put("buildings",buildings);put("trees",trees);put("landmarks",landmarks);put("facades",facades)
        put("detailedRoads",detailedRoads);put("laneGuidance",laneGuidance);put("entrances",entrances);put("lighting",lighting);put("weather",weather)
    }
    companion object {
        fun parse(json: JSONObject)=MapExperienceConfig(
            json.optBoolean("enabled",true),json.optBoolean("buildings",true),json.optBoolean("trees",true),json.optBoolean("landmarks",true),json.optBoolean("facades",true),
            json.optBoolean("detailedRoads",true),json.optBoolean("laneGuidance",true),json.optBoolean("entrances",true),
            json.optString("lighting","theme").takeIf { it in listOf("theme","clock","dawn","day","dusk","night") } ?: "theme",json.optBoolean("weather",true)
        )
    }
}
/** Atomic file shared by launcher/settings and the isolated map process. */
object MapExperience {
    const val ACTION="com.hirolifecreative.floatingmap.MAP_EXPERIENCE"
    private var stamp=Long.MIN_VALUE
    private var cached=MapExperienceConfig()
    fun read(context: Context): MapExperienceConfig {
        val file=File(context.filesDir,"map-experience.json")
        if(file.lastModified()!=stamp) {
            cached=try { MapExperienceConfig.parse(JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })) } catch(_: Exception) { MapExperienceConfig() }
            stamp=file.lastModified()
        }
        return cached
    }
    fun save(context: Context,value: MapExperienceConfig) {
        val file=AtomicFile(File(context.filesDir,"map-experience.json"));val output=file.startWrite()
        try { output.write(value.json().toString().toByteArray());file.finishWrite(output) } catch(e: Exception) { file.failWrite(output);throw e }
        stamp=Long.MIN_VALUE
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
    }
}
