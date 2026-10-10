package com.hirolifecreative.floatingmap

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Recibe "Compartir" desde Waze (o cualquier app) con un enlace waze.com/ul.
 * Extrae las coordenadas (ll=) o geocodifica la dirección (q=) y la envía
 * al mapa flotante, que dibuja el pin y la ruta.
 */
class WazeShareActivity : Activity() {

    data class Dest(val lat: Double, val lon: Double, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        Thread {
            val dest = parseSharedText(text)
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
                        this, "No encontré un destino en lo compartido", Toast.LENGTH_LONG
                    ).show()
                }
            }
            finish()
        }.start()
    }

    private fun parseSharedText(text: String): Dest? {
        val ll = Regex("[?&]ll=(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)").find(text)
        val q = Regex("[?&]q=([^&\\s]+)").find(text)
        val name = q?.groupValues?.get(1)?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (_: Exception) {
                it
            }
        } ?: "Destino de Waze"
        if (ll != null) {
            val lat = ll.groupValues[1].toDoubleOrNull()
            val lon = ll.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) return Dest(lat, lon, name)
        }
        if (q != null) {
            geocode(name)?.let { return Dest(it.first, it.second, name) }
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
