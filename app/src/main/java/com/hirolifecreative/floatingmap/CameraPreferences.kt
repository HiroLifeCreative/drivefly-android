package com.hirolifecreative.floatingmap

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Atomic storage shared by the lightweight settings process and the map process. */
object CameraPreferences {
    const val ACTION = "com.hirolifecreative.floatingmap.CAMERA_PREFERENCES"
    private var saved: CameraConfig? = null
    private var preview: CameraConfig? = null
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir,"camera-preferences.json"))
    fun current(ctx: Context): CameraConfig = preview ?: saved ?: reload(ctx)
    fun reload(ctx: Context): CameraConfig {
        preview = null
        saved = try {
            val json = JSONObject(file(ctx).openRead().bufferedReader().use { it.readText() })
            CameraConfigCodec.decode(json)
        } catch (_: Exception) { CameraConfig() }
        return saved!!
    }
    fun receive(ctx: Context,intent: Intent) {
        if (intent.getBooleanExtra("preview",false)) {
            preview = try { CameraConfigCodec.decode(JSONObject(intent.getStringExtra("cameraConfig").orEmpty())) } catch (_: Exception) { saved ?: reload(ctx) }
        } else reload(ctx)
    }
    fun preview(ctx: Context,config: CameraConfig) {
        val value = config.normalized()
        ctx.sendBroadcast(Intent(ACTION).setPackage(ctx.packageName).putExtra("preview",true)
            .putExtra("cameraConfig",CameraConfigCodec.encode(value).toString()))
    }
    fun clearPreview(ctx: Context) { ctx.sendBroadcast(Intent(ACTION).setPackage(ctx.packageName)) }
    fun save(ctx: Context,config: CameraConfig) {
        val value = config.normalized();val storage = file(ctx);val stream = storage.startWrite()
        try {
            stream.write(CameraConfigCodec.encode(value).toString().toByteArray(Charsets.UTF_8))
            storage.finishWrite(stream);saved = value;preview = null
        } catch (error: Exception) { storage.failWrite(stream);throw error }
        clearPreview(ctx)
    }
}
