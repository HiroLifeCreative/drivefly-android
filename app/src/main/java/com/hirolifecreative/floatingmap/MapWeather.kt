package com.hirolifecreative.floatingmap

import android.location.Location
import android.os.Handler
import android.os.Looper
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.precipitations.generated.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.WeakHashMap
import kotlin.math.round

/** Regional observations, never fabricated live conditions on a particular road. */
object MapWeather {
    private val handler=Handler(Looper.getMainLooper())
    private val maps=WeakHashMap<MapView,String>()
    private var observation: WeatherData.Observation?=null
    private var location: DestinationAccess.Point?=null
    private var fetchedAt=0L
    private var requestedAt=0L
    private var fetchedPoint: DestinationAccess.Point?=null
    private var busy=false
    fun attach(map: MapView,style: Style) { maps[map]="";apply(map,style) }
    private fun apply(map: MapView,style: Style) {
        val config=MapExperience.read(map.context)
        val data=observation?.takeIf { config.enabled&&config.weather&&WeatherData.applicable(it,location,System.currentTimeMillis()) }
        val key="${data?.kind}:${data?.intensity}"
        if(maps[map]==key) return
        try {
            style.removeRain();style.removeSnow()
            when(data?.kind) {
                WeatherData.Kind.RAIN -> style.setRain(rain { density(data.intensity*.6);intensity(data.intensity);opacity(.20);vignette(0.0);centerThinning(.75) })
                WeatherData.Kind.SNOW -> style.setSnow(snow { density(data.intensity*.6);intensity(data.intensity);opacity(.25);vignette(0.0) })
                else -> Unit
            }
            maps[map]=key
        } catch(_: Exception) { maps[map]="" }
    }
    private fun render() { maps.keys.toList().forEach { map ->
        if(map.isAttachedToWindow) map.mapboxMap.getStyle { apply(map,it) } else maps.remove(map)
    } }
    fun update(fix: Location) {
        if(!fix.hasAccuracy()||fix.accuracy>100||System.currentTimeMillis()-fix.time>60000) return
        location=DestinationAccess.Point(fix.latitude,fix.longitude)
        render()
        val map=maps.keys.firstOrNull { MapExperience.read(it.context).let { c -> c.enabled&&c.weather } } ?: return
        val now=System.currentTimeMillis();val point=location ?: return
        if(busy||now-requestedAt<60000) return
        if(now-fetchedAt<900000&&fetchedPoint?.let { DestinationAccess.distance(it,point)<20000 }==true) return
        busy=true;requestedAt=now
        Thread {
            val data=try {
                val lat=round(point.lat*100)/100;val lon=round(point.lon*100)/100
                val properties=fetch("https://api.weather.gov/points/$lat,$lon").getJSONObject("properties")
                val stations=fetch(properties.getString("observationStations")).getJSONArray("features")
                val station=(0 until stations.length()).mapNotNull { stations.optJSONObject(it) }.mapNotNull { feature ->
                    val coords=feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                    val p=DestinationAccess.Point(coords.optDouble(1,Double.NaN),coords.optDouble(0,Double.NaN))
                    val id=feature.optJSONObject("properties")?.optString("stationIdentifier").orEmpty()
                    val distance=DestinationAccess.distance(p,point)
                    if(!id.matches(Regex("[A-Za-z0-9]{3,12}"))||!distance.isFinite()||distance>35000) null else id to distance
                }.minByOrNull { it.second }?.first
                station?.let { WeatherData.parse(fetch("https://api.weather.gov/stations/$it/observations/latest"),System.currentTimeMillis()) }
            } catch(_: Exception) { null }
            handler.post { busy=false;fetchedAt=System.currentTimeMillis();fetchedPoint=point;observation=data;render() }
        }.start()
    }
    private fun fetch(url: String): JSONObject {
        val parsed=URL(url)
        require(parsed.protocol=="https"&&parsed.host=="api.weather.gov")
        val c=parsed.openConnection() as HttpURLConnection
        try {
            c.connectTimeout=10000;c.readTimeout=10000;c.setRequestProperty("User-Agent","DriveFly/74 Android com.hirolifecreative.floatingmap");c.setRequestProperty("Accept","application/geo+json")
            check(c.responseCode in 200..299)
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }
}
