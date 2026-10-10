package com.hirolifecreative.floatingmap

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.mapbox.maps.MapView
import java.io.File

/** Native signals bypass Kotlin handlers. Recover only after a recorded crash during a 3D session. */
object PuckRecovery {
    private var initialized=false
    private val maps=java.util.WeakHashMap<MapView,Boolean>()
    private fun marker(context: Context)=File(context.filesDir,"native-puck-active.txt")
    fun initialize(context: Context) {
        if(initialized) return
        initialized=true
        val file=marker(context)
        if(file.exists() && Build.VERSION.SDK_INT>=30) {
            val crash=try { context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,0,10).any {
                it.processName==context.packageName+":map" && it.timestamp>=file.lastModified() &&
                it.reason in listOf(ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_CRASH)
            } } catch(_: Exception) { false }
            if(crash) fallback(context)
        }
        file.delete()
    }
    fun using(map: MapView,enabled: Boolean) {
        if(!maps.containsKey(map)) map.addOnAttachStateChangeListener(object: android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { }
            override fun onViewDetachedFromWindow(v: android.view.View) { maps.remove(map);update(map.context);map.removeOnAttachStateChangeListener(this) }
        })
        maps[map]=enabled;update(map.context)
    }
    private fun update(context: Context) {
        val file=marker(context)
        try {
            if(maps.values.any { it }) { if(!file.exists()) file.writeText("3D puck active") } else file.delete()
        } catch(_: Exception) { }
    }
    fun fallback(context: Context) {
        CameraPreferences.save(context,CameraPreferences.current(context).copy(nativePuck3D=false))
        marker(context).delete()
    }
}
