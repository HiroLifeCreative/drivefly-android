package com.hirolifecreative.floatingmap

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** One response for preview, navigation and floating map. No invented route on failure. */
object RoutingClient {
    data class Result(val json: String, val duration: Double, val distance: Double, val route: RouteParser.Route,val alternatives: List<Result> = emptyList(),val trafficAware: Boolean = false,val annotation: RouteAnnotationData = RouteAnnotationData())
    class Failure(message: String) : Exception(message)
    fun fetch(lon: Double, lat: Double, destLon: Double, destLat: Double, token: String, bearing: Double? = null): Result {
        val coordinates = "$lon,$lat;$destLon,$destLat"
        val bearingQuery = bearing?.takeIf { it.isFinite() }?.let { "&bearings=${((it%360)+360)%360},45;" }.orEmpty()
        val endpoints = mutableListOf<String>()
        if (token.startsWith("pk.")) endpoints += "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/$coordinates?overview=full&geometries=geojson&steps=true&alternatives=true&annotations=closure,maxspeed,congestion_numeric,distance&language=es${bearingQuery}&access_token=${URLEncoder.encode(token, "UTF-8")}"
        endpoints += "https://router.project-osrm.org/route/v1/driving/$coordinates?overview=full&geometries=geojson&steps=true&alternatives=true${bearingQuery}"
        var noRoute = false
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "DriveFly/41 Android")
                if (connection.responseCode !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    if (error != null && JSONObject(error).optString("code") in listOf("NoRoute", "NoSegment")) {
                        if (endpoint.contains("mapbox.com")) throw Failure("No hay una ruta accesible con el tráfico y los cierres registrados. Prueba otro punto.")
                        noRoute = true
                    }
                    continue
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val body = JSONObject(json)
                if (body.optString("code") in listOf("NoRoute", "NoSegment")) {
                    if (endpoint.contains("mapbox.com")) throw Failure("No hay una ruta accesible con el tráfico y los cierres registrados. Prueba otro punto.")
                    noRoute = true;continue
                }
                return decode(body.put("drivefly_traffic",endpoint.contains("mapbox.com")).toString())
            } catch (error: Exception) { if (error is Failure) throw error /* Network failures may use the fallback with a visible warning. */ }
            finally { connection?.disconnect() }
        }
        throw Failure(if (noRoute) "No hay acceso por carretera a ese punto. Prueba un punto cercano." else "No se pudo conectar al servicio de rutas. Comprueba Internet y vuelve a intentar.")
    }
    fun decode(json: String): Result {
        val body = JSONObject(json)
        require(body.optString("code") == "Ok") { "No hay ruta disponible" }
        val routes = body.getJSONArray("routes")
        fun result(index: Int): Result {
            val r = routes.getJSONObject(index)
            val single = JSONObject().put("code","Ok").put("routes",org.json.JSONArray().put(r)).put("drivefly_traffic",body.optBoolean("drivefly_traffic",false)).toString()
            val parsed = RouteParser.parse(single)
            return Result(single,r.getDouble("duration"),r.getDouble("distance"),parsed,trafficAware=body.optBoolean("drivefly_traffic",false),annotation=RouteAnnotationData.parse(r,parsed.coordinates.size))
        }
        val primary = result(0)
        val alternatives = (1 until minOf(routes.length(),3)).mapNotNull { i -> try { result(i) } catch (_: Exception) { null } }
            .filter { it.route.coordinates != primary.route.coordinates }.distinctBy { it.route.coordinates }
        return primary.copy(alternatives = alternatives)
    }
}
