package com.hirolifecreative.floatingmap

import android.os.Handler
import android.os.Looper
import com.mapbox.maps.MapView
import com.mapbox.bindgen.Value
import java.util.Calendar
import java.util.WeakHashMap

/** Updates presets in place, without resetting the route or the camera. */
object MapLighting {
    private val maps=WeakHashMap<MapView,String>()
    private val handler=Handler(Looper.getMainLooper())
    private var running=false
    private fun preset(map: MapView)=MapExperience.read(map.context).lightPreset(Appearance.dark(map.context),Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    fun observe(map: MapView) {
        maps[map]=preset(map)
        if(!running) { running=true;handler.postDelayed(tick,60000) }
    }
    private val tick=object: Runnable {
        override fun run() {
            maps.keys.toList().forEach { map ->
                if(!map.isAttachedToWindow) { maps.remove(map);return@forEach }
                if(MapExperience.read(map.context).enabled) {
                    val value=preset(map)
                    if(value!=maps[map]) map.mapboxMap.getStyle { style ->
                        style.setStyleImportConfigProperty("basemap","lightPreset",Value.valueOf(value));maps[map]=value
                    }
                }
            }
            running=maps.isNotEmpty()
            if(running) handler.postDelayed(this,60000)
        }
    }
}
