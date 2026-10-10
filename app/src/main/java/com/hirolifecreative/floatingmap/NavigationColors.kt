package com.hirolifecreative.floatingmap

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

data class NavigationColorConfig(val route: String="#007AFF",val pickup: String="#16A765",val delivery: String="#FF3B30",val other: String="#172A4B") {
    fun value(key: String)=when(key) { "pickup"->pickup;"delivery"->delivery;"other"->other;else->route }
    fun withColor(key: String,value: String): NavigationColorConfig {
        val color=normalize(value) ?: throw IllegalArgumentException("Usa un color de seis dígitos")
        return when(key) { "pickup"->copy(pickup=color);"delivery"->copy(delivery=color);"other"->copy(other=color);"route"->copy(route=color);else->throw IllegalArgumentException("Color desconocido") }
    }
    fun json()=JSONObject().put("route",route).put("pickup",pickup).put("delivery",delivery).put("other",other)
    companion object {
        fun normalize(value: String): String? {
            val hex=value.trim().removePrefix("#")
            return if(hex.matches(Regex("[0-9a-fA-F]{6}"))) "#"+hex.uppercase(java.util.Locale.ROOT) else null
        }
        fun parse(json: JSONObject): NavigationColorConfig {
            val defaults=NavigationColorConfig()
            fun field(key: String)=normalize(json.optString(key)) ?: defaults.value(key)
            return NavigationColorConfig(field("route"),field("pickup"),field("delivery"),field("other"))
        }
        fun casing(color: String): String {
            val n=(normalize(color) ?: "#007AFF").substring(1).toInt(16)
            fun dark(shift: Int)=((n shr shift and 255)*.62).toInt()
            return "#%02X%02X%02X".format(java.util.Locale.ROOT,dark(16),dark(8),dark(0))
        }
    }
}
object NavigationColors {
    const val ACTION="com.hirolifecreative.floatingmap.NAVIGATION_COLORS"
    private var stamp=Long.MIN_VALUE
    private var cached=NavigationColorConfig()
    fun read(context: Context): NavigationColorConfig {
        val file=File(context.filesDir,"navigation-colors.json")
        if(file.lastModified()!=stamp) {
            cached=try { NavigationColorConfig.parse(JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })) } catch(_: Exception) { NavigationColorConfig() }
            stamp=file.lastModified()
        }
        return cached
    }
    fun save(context: Context,value: NavigationColorConfig) {
        val safe=NavigationColorConfig.parse(value.json())
        val file=AtomicFile(File(context.filesDir,"navigation-colors.json"));val out=file.startWrite()
        try { out.write(safe.json().toString().toByteArray());file.finishWrite(out) } catch(e: Exception) { file.failWrite(out);throw e }
        stamp=Long.MIN_VALUE
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
    }
    fun pin(context: Context,kind: DestinationKind): String {
        val c=read(context)
        return when(kind) { DestinationKind.PICKUP->c.pickup;DestinationKind.DELIVERY->c.delivery;else->c.other }
    }
}
