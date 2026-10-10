package com.hirolifecreative.floatingmap

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Aparece como opción ("DriveFly") cuando otra app —p. ej. DoorDash—
 * pide navegar con un enlace geo: o google.navigation:.
 * Toma el destino y lo dibuja en el mapa flotante: pin + ruta + ETA.
 */
class GeoNavigateActivity : Activity() {

    data class Dest(val lat: Double, val lon: Double, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        Thread {
            val dest = parseUri(uri)
            runOnUiThread {
                if (dest != null) {
                    try {
                        val svc = Intent(this, FloatingMapService::class.java)
                            .setAction(FloatingMapService.ACTION_DESTINATION)
                            .putExtra("lat", dest.lat)
                            .putExtra("lon", dest.lon)
                            .putExtra("name", dest.name)
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
                        else startService(svc)
                        Toast.makeText(
                            this, "Destino enviado al mapa flotante", Toast.LENGTH_SHORT
                        ).show()
                    } catch (_: Exception) {
                        Toast.makeText(
                            this, "Abre primero el mapa flotante", Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this, "No encontré un destino válido", Toast.LENGTH_LONG
                    ).show()
                }
            }
            finish()
        }.start()
    }

    private fun parseUri(uri: Uri?): Dest? {
        if (uri == null) return null
        // geo:lat,lon?q=...  |  google.navigation:q=...
        val ssp = uri.schemeSpecificPart.orEmpty()
        val coordPart = ssp.substringBefore("?")
        val qRaw = ssp.substringAfter("?", "").split("&")
            .firstOrNull { it.startsWith("q=") }
            ?.removePrefix("q=").orEmpty()
        val q = try {
            URLDecoder.decode(qRaw, "UTF-8")
        } catch (_: Exception) {
            qRaw
        }
        // Coordenadas directas en geo:lat,lon
        Regex("^(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)").find(coordPart)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lon = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                val name = q.substringBefore("(").trim().ifEmpty { "Destino" }
                return Dest(lat, lon, name)
            }
        }
        if (q.isNotBlank()) {
            // q puede ser "lat,lon" o una dirección escrita
            Regex("^(-?\\d+\\.?\\d*),\\s*(-?\\d+\\.?\\d*)").find(q)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lon = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    return Dest(lat, lon, q.substringBefore("(").trim())
                }
            }
            geocode(q)?.let { return Dest(it.first, it.second, q.take(80)) }
        }
        return null
    }

    private fun geocode(query: String): Pair<Double, Double>? {
        return try {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                        URLEncoder.encode(query, "UTF-8")
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MapaFlotante/1.0 (uso personal)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            Pair(o.getDouble("lat"), o.getDouble("lon"))
        } catch (_: Exception) {
            null
        }
    }
}
