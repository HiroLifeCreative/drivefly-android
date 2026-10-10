package com.hirolifecreative.floatingmap

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

object PlacesClient {
    data class Business(val name: String,val address: String,val lon: Double,val lat: Double,val id: String?,val publishedDetails: Details? = null)
    data class Details(val hours: String?,val timezone: String?,val permanentlyClosed: Boolean,val phone: String?,val address: String? = null)
    private fun encode(value: String) = URLEncoder.encode(value,"UTF-8")
    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000;conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent","DriveFly/40 Android")
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException(if (code == 401 || code == 403) "Tu cuenta o token no permite consultar negocios. Revisa el acceso a Search Box en Mapbox." else "No se pudieron consultar los negocios. Vuelve a intentar.")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
    fun nearby(category: String,lon: Double,lat: Double,token: String): List<Business> {
        val endpoint = "https://api.mapbox.com/search/searchbox/v1/category/${encode(category)}?access_token=${encode(token)}&language=es&limit=25&show_closed_pois=true"
        val boxes = listOf(-1 to -1,-1 to 0,0 to -1,0 to 0).map { (x,y) ->
            val west = (lon+x*.04).coerceIn(-179.99,179.95);val south = (lat+y*.04).coerceIn(-89.99,89.95)
            "$west,$south,${west+.04},${south+.04}"
        }
        val urls = listOf("$endpoint&proximity=$lon,$lat&radius=0.04") + boxes.map { "$endpoint&proximity=$lon,$lat&bbox=$it" }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            val results = pool.invokeAll(urls.map { url -> java.util.concurrent.Callable { parse(get(url)) } })
            var firstError: Exception? = null
            val businesses = results.flatMap { future -> try { future.get() } catch (e: Exception) { firstError = e;emptyList() } }
            if (businesses.isEmpty() && firstError != null) throw firstError!!
            return unique(businesses)
        } finally { pool.shutdownNow() }
    }
    fun unique(businesses: List<Business>): List<Business> = businesses.distinctBy {
        it.id ?: "${it.name.lowercase(java.util.Locale.ROOT)}:${it.lon}:${it.lat}"
    }.take(125)
    fun mergeDetails(old: Details?,fresh: Details) = fresh.copy(
        hours = fresh.hours ?: old?.hours,timezone = fresh.timezone ?: old?.timezone,
        permanentlyClosed = fresh.permanentlyClosed || old?.permanentlyClosed == true,
        phone = fresh.phone ?: old?.phone,address = fresh.address ?: old?.address)
    fun parse(body: JSONObject): List<Business> {
        val array = body.getJSONArray("features")
        return (0 until array.length()).mapNotNull { i ->
            try {
                val feature = array.getJSONObject(i);val p = feature.getJSONObject("properties");val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                val lon = coords.getDouble(0);val lat = coords.getDouble(1)
                if (!lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -90.0..90.0) return@mapNotNull null
                Business(p.getString("name"),p.optString("full_address",p.optString("place_formatted")),lon,lat,p.optString("mapbox_id").takeIf { it.isNotBlank() },decodeDetails(p).takeIf { it.hours != null || it.permanentlyClosed })
            } catch (_: Exception) { null }
        }
    }
    fun resolve(name: String,lon: Double,lat: Double,token: String): String? {
        val matches = parse(get("https://api.mapbox.com/search/searchbox/v1/forward?q=${encode(name)}&types=poi&limit=5&proximity=$lon,$lat&access_token=${encode(token)}"))
        return matches.filter { business ->
            val latitude = Math.toRadians(lat);val otherLatitude = Math.toRadians(business.lat)
            val dLat = otherLatitude-latitude;val dLon = Math.toRadians(business.lon-lon)
            val a = kotlin.math.sin(dLat/2)*kotlin.math.sin(dLat/2) + kotlin.math.cos(latitude)*kotlin.math.cos(otherLatitude)*kotlin.math.sin(dLon/2)*kotlin.math.sin(dLon/2)
            val distance = 6371000*2*kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0,1.0)))
            business.name.equals(name,true) && distance < 50
        }.singleOrNull()?.id
    }
    private fun decodeDetails(body: JSONObject): Details = Details(
        body.optString("opening_hours").takeIf { it.isNotBlank() && it != "null" },
        body.optJSONObject("telemetry")?.optJSONObject("tags")?.optString("timezone")?.takeIf { it.isNotBlank() },
        body.optBoolean("permanently_closed",false),body.optString("phone").takeIf { it.isNotBlank() && it != "null" },
        body.optString("full_address").takeIf { it.isNotBlank() && it != "null" })
    fun details(id: String,token: String): Details = decodeDetails(get("https://api.mapbox.com/places/v1/details/retrieve/${encode(id)}?access_token=${encode(token)}"))
    fun detailsBatch(ids: List<String>,token: String): Map<String,Details> {
        if (ids.isEmpty()) return emptyMap()
        val conn = URL("https://api.mapbox.com/places/v1/details/retrieve?access_token=${encode(token)}").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST";conn.doOutput = true
            conn.connectTimeout = 12000;conn.readTimeout = 20000
            conn.setRequestProperty("Content-Type","application/json")
            conn.outputStream.use { it.write(JSONObject().put("ids",org.json.JSONArray(ids.distinct().take(100))).toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Horarios no disponibles")
            val body = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return parseBatch(body)
        } finally { conn.disconnect() }
    }
    fun parseBatch(body: JSONObject): Map<String,Details> {
        val records = body.optJSONArray("results") ?: return emptyMap()
        return (0 until records.length()).mapNotNull { i ->
            val record = records.optJSONObject(i) ?: return@mapNotNull null
            val id = record.optString("mapbox_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to decodeDetails(record)
        }.toMap()
    }
}
