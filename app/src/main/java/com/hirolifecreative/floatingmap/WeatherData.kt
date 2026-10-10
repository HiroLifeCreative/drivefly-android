package com.hirolifecreative.floatingmap

import org.json.JSONObject
import java.time.Instant

object WeatherData {
    enum class Kind { NONE,RAIN,SNOW }
    data class Observation(val location: DestinationAccess.Point,val time: Long,val kind: Kind,val intensity: Double)
    fun parse(body: JSONObject,now: Long): Observation? {
        val p=body.optJSONObject("properties") ?: return null
        val time=try { Instant.parse(p.optString("timestamp")).toEpochMilli() } catch(_: Exception) { return null }
        if(now-time !in -300000L..5400000L) return null
        val xy=body.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val lon=xy.optDouble(0,Double.NaN);val lat=xy.optDouble(1,Double.NaN)
        if(!lon.isFinite()||!lat.isFinite()||lon !in -180.0..180.0||lat !in -90.0..90.0) return null
        val weather=p.optJSONArray("presentWeather") ?: return null
        val values=(0 until weather.length()).mapNotNull { weather.optJSONObject(it) }
        val precipitation=values.firstOrNull { it.optString("weather") in listOf("snow","rain","drizzle") }
        val kind=when(precipitation?.optString("weather")) { "snow" -> Kind.SNOW;"rain","drizzle" -> Kind.RAIN;else -> Kind.NONE }
        val intensity=if(kind==Kind.NONE) 0.0 else when(precipitation?.optString("intensity")) { "heavy" -> .25;"light" -> .10;else -> .16 }
        return Observation(DestinationAccess.Point(lat,lon),time,kind,intensity)
    }
    fun applicable(observation: Observation?,location: DestinationAccess.Point?,now: Long): Boolean=
        observation!=null&&location!=null&&now-observation.time in -300000L..5400000L&&DestinationAccess.distance(observation.location,location)<=35000
}
