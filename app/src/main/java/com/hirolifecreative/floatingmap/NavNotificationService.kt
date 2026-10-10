package com.hirolifecreative.floatingmap

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Lee las notificaciones de Waze y DoorDash para tomar el destino
 * automáticamente: pin + ruta en el mapa flotante sin copiar direcciones.
 * Requiere "Acceso a notificaciones" en Ajustes.
 */
class NavNotificationService : NotificationListenerService() {

    private var lastKey = ""
    private var lastTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != PKG_WAZE && pkg !in DasherAutomation.packages) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        if (pkg in DasherAutomation.packages) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
            val texts=listOf(title,text,bigText)+lines
            val offer=DasherOfferPolicy.isOffer(texts)||Regex("(?:nuevo pedido|new order)",RegexOption.IGNORE_CASE).containsMatchIn(title+" "+text)
            if(offer) DasherAutomation.announce(this,texts,true,false)
            DasherAddressParser.select(texts)?.let { DasherAutomation.submit(this,it) }
            return
        }

        if (pkg == PKG_WAZE) {
            val info = listOf(title, text, bigText).map { it.trim() }
                .filter { it.isNotBlank() }.distinct().joinToString(" · ")
            if (info.isNotBlank()) {
                sendBroadcast(
                    Intent(ACTION_WAZE_INFO)
                        .putExtra("text", info)
                        .setPackage(packageName)
                )
            }
        }

        // Intenta extraer una dirección y convertirla en destino (pin + ruta)
        val address = extractAddress("$title $text $bigText") ?: return
        val key = "$pkg|$address"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastTime < 5 * 60 * 1000) return
        lastKey = key
        lastTime = now
        thread {
            val ll = geocode(address) ?: return@thread
            val src = if (pkg == PKG_WAZE) "Waze" else "DoorDash"
            sendBroadcast(
                Intent(ACTION_DESTINATION)
                    .putExtra("lat", ll.first)
                    .putExtra("lon", ll.second)
                    .putExtra("name", address)
                    .putExtra("source", src)
                    .setPackage(packageName)
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != PKG_WAZE) return
        sendBroadcast(Intent(ACTION_WAZE_CLEARED).setPackage(packageName))
    }

    /** Extrae algo con pinta de "123 Calle Principal, Ciudad" del texto. */
    private fun extractAddress(raw: String): String? {
        var s = " $raw "
        val prefixes = listOf(
            "head to", "navigate to", "drive to", "deliver to", "delivery to",
            "pickup at", "pick up at", "drop off at", "drop-off at",
            "on your way to", "heading to",
            "dirígete a", "navega a", "conduce a", "entrega en", "recoge en",
            "llegando a"
        )
        for (p in prefixes) {
            val i = s.lowercase().indexOf(p)
            if (i >= 0) {
                s = s.substring(i + p.length)
                break
            }
        }
        s = s.replace(
            Regex("""[·•\-–—|]\s*\d+\s*(min|mins|h|hr|hour).*""", RegexOption.IGNORE_CASE), " "
        )
        s = s.replace(Regex("""\(\d+\s*(min|mins).*?\)""", RegexOption.IGNORE_CASE), " ")
        val m = Regex("""\d{1,6}\s+[A-Za-z0-9.'\- ]{2,40},[^,]{2,40}""").find(s)
        return m?.value?.trim()?.take(120)
    }

    private fun geocode(q: String): Pair<Double, Double>? {
        return try {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                        URLEncoder.encode(q, "UTF-8")
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MapaFlotante/1.0 (uso personal)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val arr = org.json.JSONArray(body)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            Pair(o.getDouble("lat"), o.getDouble("lon"))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PKG_WAZE = "com.waze"
        const val PKG_DOORDASH = "com.dd.doordash"
        const val ACTION_WAZE_INFO = "com.hirolifecreative.floatingmap.WAZE_INFO"
        const val ACTION_WAZE_CLEARED = "com.hirolifecreative.floatingmap.WAZE_CLEARED"
        const val ACTION_DESTINATION = "com.hirolifecreative.floatingmap.DESTINATION"
    }
}
