package com.hirolifecreative.floatingmap

import android.graphics.*
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.geojson.*
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** One cached OSM dataset feeds both maps; native style mutations stay on main. */
object RoadSigns {
    private val handler = Handler(Looper.getMainLooper())
    private val maps = java.util.WeakHashMap<MapView,Boolean>()
    private val kinds = listOf("traffic_signals","stop","give_way","speed_camera")
    private var signs = emptyList<RoadSignData.Sign>()
    private var crossings = emptyList<CrosswalkData.Crossing>()
    private data class Dataset(val signs: List<RoadSignData.Sign>,val crossings: List<CrosswalkData.Crossing>)
    private var center: Location? = null
    private var busy = false
    private var nextAttempt = 0L
    fun attach(map: MapView,style: Style) {
        maps[map] = true
        style.addSource(geoJsonSource("df-crosswalk-surface") { featureCollection(crosswalkFeatures(true)) })
        style.addLayer(fillLayer("df-crosswalk-asphalt","df-crosswalk-surface") {
            slot("top");minZoom(14.0);fillColor(if(Appearance.dark(map.context)) "#344F68" else "#9BAAB1");fillOpacity(.65);fillAntialias(true)
        })
        style.addSource(geoJsonSource("df-crosswalks") { featureCollection(crosswalkFeatures()) })
        style.addLayer(fillLayer("df-crosswalk-paint","df-crosswalks") {
            slot("top");minZoom(14.0);fillColor("#FFFFFF");fillOpacity(1.0);fillEmissiveStrength(1.0);fillAntialias(true)
        })
        // A small screen-space edge keeps geographic paint legible in the forward view.
        style.addLayer(lineLayer("df-crosswalk-paint-edge","df-crosswalks") {
            slot("top");minZoom(14.0);lineColor("#FFFFFF");lineWidth(.6)
            lineOpacity(.95);lineEmissiveStrength(1.0)
        })
        kinds.forEach { kind ->
            style.addImage("df-sign-$kind",icon(kind))
            style.addSource(geoJsonSource("df-signs-$kind") { featureCollection(features(kind)) })
            style.addLayer(symbolLayer("df-sign-layer-$kind","df-signs-$kind") {
                slot("top");minZoom(14.0)
                iconImage("df-sign-$kind");iconSize(if (kind=="traffic_signals") .693 else .572)
                iconAllowOverlap(false);iconIgnorePlacement(false)
            })
        }
        NavigationSession.latest?.let { update(it) }
    }
    private fun features(kind: String) = FeatureCollection.fromFeatures(signs.filter { it.kind==kind }.map { Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)) })
    private fun crosswalkFeatures(surface: Boolean=false) = FeatureCollection.fromFeatures(crossings.flatMap { crossing ->
        (if(surface) CrosswalkData.surface(crossing) else CrosswalkData.stripes(crossing)).map { ring -> Feature.fromGeometry(Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.lon,it.lat) }))) }
    })
    fun update(location: Location) {
        if (Looper.myLooper()!=Looper.getMainLooper()) { handler.post { update(location) };return }
        MapWeather.update(location)
        val now = android.os.SystemClock.uptimeMillis()
        if (busy || now<nextAttempt || center?.let { location.distanceTo(it)<800 }==true) return
        busy = true;nextAttempt = now+60000
        val requested = Location(location)
        Thread {
            try {
                val query = """[out:json][timeout:15];
                    node[highway~"^(traffic_signals|stop|give_way|crossing|speed_camera)$"](around:1800,${requested.latitude},${requested.longitude})->.signs;
                    way(bn.signs)[highway]->.roads;
                    way[footway=crossing](around:1800,${requested.latitude},${requested.longitude})->.crossings;
                    (.signs;.roads;.crossings;);out body geom;"""
                var result: Dataset? = null
                for (endpoint in listOf("https://overpass-api.de/api/interpreter","https://overpass.kumi.systems/api/interpreter")) {
                    try { result = fetch(endpoint,query);break } catch (_: Exception) { }
                }
                val fetched = result ?: throw IllegalStateException("No signal data available")
                handler.post {
                    signs = fetched.signs;crossings = fetched.crossings;center = requested;busy = false
                    maps.keys.toList().forEach { map ->
                        if (map.isAttachedToWindow) map.mapboxMap.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>("df-crosswalk-surface")?.featureCollection(crosswalkFeatures(true))
                            style.getSourceAs<GeoJsonSource>("df-crosswalks")?.featureCollection(crosswalkFeatures())
                            kinds.forEach { kind -> style.getSourceAs<GeoJsonSource>("df-signs-$kind")?.featureCollection(features(kind)) }
                        }
                    }
                }
            } catch (_: Exception) { handler.post { busy = false } }
        }.start()
    }
    private fun fetch(endpoint: String,query: String): Dataset {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST";connection.doOutput = true
            connection.connectTimeout = 10000;connection.readTimeout = 18000
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            connection.setRequestProperty("User-Agent","DriveFly/73 Android")
            connection.outputStream.use { it.write(("data="+URLEncoder.encode(query,"UTF-8")).toByteArray()) }
            check(connection.responseCode in 200..299)
            val body=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return Dataset(RoadSignData.parse(body),CrosswalkData.parse(body))
        } finally { connection.disconnect() }
    }
    private fun icon(kind: String): Bitmap {
        val b = Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun polygon(n: Int,r: Float,rotation: Double): Path = Path().apply {
            for (i in 0 until n) {
                val a = Math.toRadians(rotation+i*360.0/n);val x = 48+r*kotlin.math.cos(a).toFloat();val y = 48+r*kotlin.math.sin(a).toFloat()
                if (i==0) moveTo(x,y) else lineTo(x,y)
            };close()
        }
        when (kind) {
            "traffic_signals" -> {
                p.color = Color.WHITE;c.drawRoundRect(25f,2f,71f,94f,12f,12f,p)
                p.color = Color.rgb(24,28,34);c.drawRoundRect(28f,5f,68f,91f,10f,10f,p)
                listOf(Color.rgb(255,75,75),Color.rgb(255,203,53),Color.rgb(48,205,123)).forEachIndexed { i,col -> p.color=col;c.drawCircle(48f,21f+i*27f,10f,p) }
            }
            "stop" -> {
                p.color=Color.WHITE;c.drawPath(polygon(8,46f,22.5),p)
                p.color=Color.rgb(222,48,59);c.drawPath(polygon(8,41f,22.5),p)
                p.color=Color.WHITE;p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textSize=24f
                c.drawText("STOP",48f,48f-(p.fontMetrics.ascent+p.fontMetrics.descent)/2,p)
            }
            "give_way" -> {
                p.color=Color.rgb(222,48,59);c.drawPath(polygon(3,46f,90.0),p)
                p.color=Color.WHITE;c.drawPath(polygon(3,34f,90.0),p)
            }
            else -> {
                p.color=Color.WHITE;c.drawRoundRect(3f,3f,93f,93f,18f,18f,p)
                p.color=Color.rgb(24,76,135);c.drawRoundRect(7f,7f,89f,89f,14f,14f,p)
                p.color=Color.WHITE
                c.drawRoundRect(18f,32f,78f,70f,6f,6f,p);c.drawRect(28f,24f,45f,34f,p)
                p.color=Color.rgb(24,76,135);c.drawCircle(49f,51f,12f,p)
            }
        }
        return b
    }
}
