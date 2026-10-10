package com.hirolifecreative.floatingmap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.CircleLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos

/**
 * Mapa flotante NATIVO (Mapbox Maps SDK v11, sin WebView).
 * - Puck de navegación como capa nativa (halo + flecha azul)
 * - Cámara 3D inclinada que sigue el rumbo en movimiento
 * - Edificios 3D, ruta OSRM, semáforos y stops de OpenStreetMap
 * - Ventana movible, redimensionable, con botón de mapa para ocultar
 */
class FloatingMapService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    companion object {
        @Volatile var isRunning = false
            private set
        const val ACTION_DESTINATION = "com.hirolifecreative.floatingmap.DESTINATION"
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var root: FrameLayout
    private lateinit var locationManager: LocationManager
    private lateinit var wazeBtn: Button
    private var eyeBtn: ImageView? = null
    private var clearDestBtn: ImageView? = null
    // Nuevas funciones v25
    private var hudMode = false
    private var speedAlertEnabled = false
    private var autoSizeEnabled = false
    private var lastSpeedLimit: Int? = null
    private var lastSpeedAlertTime = 0L
    private var lastSpeedLimitFetch = 0L
    private var fgCheckHandler: android.os.Handler? = null
    private var fgCheckRunnable: Runnable? = null
    private var closeZoneView: android.view.View? = null
    private var restoreBtn: ImageView? = null
    private lateinit var resizeHandle: ImageView
    private var tripBar: TextView? = null
    private var infoBanner: TextView? = null
    private var bannerHide: Runnable? = null
    private var finishedDasherKey = ""
    private var lastRouteWasDasher = false
    private var collapsed = false
    private var collapseAnimating = false
    private var currentLoc: Location? = null
    private var destLat: Double? = null
    private var routeRequest = 0
    private var pendingRoute = false
    private var displayedRouteJson: String? = null
    private var displayedInstruction = ""
    private val orderAnnouncements=OrderAnnouncementGate()
    private var pendingDasherMerchant=""
    private var pendingDasherUnit=""
    private var dasherRequest = 0
    private var pendingDasherAddress: Pair<String,String>? = null
    private var dasherKey = ""
    private var dasherAttempt = 0L
    private var dasherRetryAfter = 0L
    private var dasherResolvingKey: String? = null
    private val dasherHandler = android.os.Handler(Looper.getMainLooper())
    private var lastRouteRender = 0L
    private var followCamera = true
    private val sharedNavigation: (NavigationSession.State?) -> Unit = { state -> renderSharedRoute(state) }
    private var destLon: Double? = null

    private var mapView: MapView? = null
    private var styleReady = false
    private var cameraInit = false
    private var lastCamBearing: Double? = null
    private var smoothB = -1.0
    private var lastSignalCenter: Location? = null
    private var signalsInFlight = false

    private val locationListener = LocationListener { loc: Location ->
        val now = SystemClock.elapsedRealtime()
        val acc = if (loc.hasAccuracy()) loc.accuracy else 9999f
        val prev = lastGoodLoc
        // Ignora fixes viejos (más de 1 min)
        if (System.currentTimeMillis() - loc.time > 60000) return@LocationListener
        // Ignora fixes malos (red) si tenemos uno bueno y fresco: evita saltos locos
        val prevAcc = if (prev != null && prev.hasAccuracy()) prev.accuracy else 9999f
        if (prev != null && now - lastGoodTime < 20000 && acc > 80f && prevAcc < 40f) {
            return@LocationListener
        }
        lastGoodLoc = loc
        lastGoodTime = now
        currentLoc = loc
        onLocationUpdate(loc)
        if (pendingRoute) { destLat?.let { lat -> destLon?.let { lon -> fetchRoute(lat,lon) } } }
    }
    private var lastGoodLoc: Location? = null
    private var lastGoodTime = 0L

    private val wazeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                DasherAutomation.ORDER_VOICE -> {
                    val config=VoicePreferences.read(ctx)
                    if(config.orderAnnouncements && config.mode!="off") orderAnnouncements.observe(
                        intent.getBooleanExtra("offer",false),intent.getStringExtra("offerKey").orEmpty(),
                        intent.getStringExtra("merchant").orEmpty(),intent.getBooleanExtra("pickup",false),android.os.SystemClock.elapsedRealtime()
                    )?.let { NavigationVoice.speak(ctx,it,enqueue=true) }
                }
                NavigationColors.ACTION -> { mapView?.let { loadNativeStyle(it) } }
                MapExperience.ACTION -> { mapView?.let { loadNativeStyle(it) } }
                Appearance.ACTION -> {
                    if (::root.isInitialized) Appearance.applyTree(root)
                    mapView?.let { loadNativeStyle(it) }
                }
                "com.hirolifecreative.floatingmap.DASHER_SCAN_STATUS" -> {
                    if (NavigationSession.state == null && pendingDasherAddress == null && System.currentTimeMillis()-dasherAttempt > 15000)
                        dasherStatus(intent.getStringExtra("status").orEmpty())
                }
                NavNotificationService.ACTION_WAZE_INFO -> {
                    val t = intent.getStringExtra("text") ?: return
                    showBanner(t)
                }
                NavNotificationService.ACTION_DESTINATION -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    val name = intent.getStringExtra("name") ?: "Destino"
                    val src = intent.getStringExtra("source").orEmpty()
                    if (lat != 0.0 || lon != 0.0) {
                        showDestination(lat, lon, if (src == "Waze") "Waze: $name" else name)
                        val label = if (src.isNotBlank()) "$src: $name" else name
                        showBanner(label)
                    }
                }
                NavNotificationService.ACTION_WAZE_CLEARED -> {
                    if (NavigationSession.state?.name?.startsWith("Waze:") == true) clearNav()
                    try {
                        wazeBtn.post { wazeBtn.visibility = View.GONE }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // ================= Ciclo de vida del servicio =================

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        try {
            onCreateInternal()
            NavigationSession.observe(this,sharedNavigation)
        } catch (e: Throwable) {
            CrashLog.save(this,e)
            try {
                Toast.makeText(
                    this,
                    "Error Mapa: ${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    private fun onCreateInternal() {
        startAsForeground()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WindowManager::class.java)
        locationManager = getSystemService(LocationManager::class.java)

        val density = resources.displayMetrics.density
        val w = (340 * density).toInt()
        val h = (300 * density).toInt()
        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 300
            // Transparencia ajustable (50-100%)
            val prefs = getSharedPreferences("mapa_flotante", MODE_PRIVATE)
            alpha = prefs.getFloat("window_alpha", 1.0f).coerceIn(0.5f, 1.0f)
        }

        val cornerR = 28f * density
        root = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = cornerR
                setColor(Appearance.background(this@FloatingMapService,Color.WHITE))
            }
            clipToOutline = true
            elevation = 8f * density
        }

        val handleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val hp = (40 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, hp
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val handleLabel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        val gripIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_grip)
            val gs = (20 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(gs, gs).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (4 * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        val handleText = TextView(this).apply {
            text = "DriveFly"
            setTextColor(Appearance.text(this@FloatingMapService,Color.parseColor("#333333")))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }
        handleLabel.addView(gripIcon)
        handleLabel.addView(handleText)

        wazeBtn = Button(this).apply {
            text = "Waze →"
            visibility = View.GONE
            setTextColor(Color.WHITE)
            background = DriveFlyUi.shape(this@FloatingMapService, DriveFlyUi.BLUE, 14)
            isAllCaps = false
            stateListAnimator = null
            textSize = 12f
            val bp = (4 * density).toInt()
            setPadding(bp * 3, bp, bp * 3, bp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = (8 * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener { openInWaze() }
        }

        handleBar.addView(handleLabel)
        handleBar.addView(wazeBtn)

        // Botón único: ocultar el mapa (lado derecho de la barra)
        eyeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_map_hide)
            contentDescription = "Ocultar mapa flotante"
            val es = (32 * density).toInt()
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#007AFF")))
            }
            layoutParams = LinearLayout.LayoutParams(es, es).apply {
                rightMargin = (8 * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener { toggleCollapse() }
        }
        handleBar.addView(eyeBtn)

        // Contenedor del mapa nativo (reemplaza al WebView)
        val mapContainer = FrameLayout(this).apply {
            val hp = (40 * density).toInt()
            val m = (8 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = hp; leftMargin = m; rightMargin = m; bottomMargin = m }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 20f * density)
                }
            }
            clipToOutline = true
            setBackgroundColor(Appearance.background(this@FloatingMapService,Color.parseColor("#F2F2F7")))
        }

        val prefs = getSharedPreferences("mapa_flotante", MODE_PRIVATE)
        val token = prefs.getString("mapbox_token", "").orEmpty().trim()
        if (token.isEmpty()) {
            val hint = TextView(this).apply {
                text = "Pega tu token de Mapbox en la app para ver el mapa"
                setTextColor(Appearance.text(this@FloatingMapService,Color.parseColor("#666666")))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            mapContainer.addView(hint)
        } else {
            try {
                MapboxOptions.accessToken = token
                // TextureView participates in the same rounded clipping, alpha and scale
                // as the frame. A separate SurfaceView can outlive the closing frame.
                val mv = MapView(this, com.mapbox.maps.MapInitOptions(this, textureView = true))
                // Mapbox busca el LifecycleOwner en el árbol de vistas (ViewTreeLifecycleOwner),
                // no en el contexto. Como esto corre en un Service, hay que asignarlo manualmente.
                // Se usa reflexión porque la clase no está en el classpath de compilación.
                try {
                    val vtlo = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                    val setM = vtlo.getMethod("set", android.view.View::class.java, LifecycleOwner::class.java)
                    setM.invoke(null, mv, this)
                } catch (e: Throwable) {
                    showBanner("No se pudo asignar ciclo de vida: ${e.message}")
                }
                mv.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                mapContainer.addView(mv)
                mapView = mv
                // Retrasar initNativeMap hasta que la vista esté acoplada a la ventana.
                // Si loadStyle se llama antes del attach, el renderer nativo puede tumbar el proceso.
                mv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        mv.removeOnAttachStateChangeListener(this)
                        try {
                            initNativeMap(mv)
                        } catch (e: Throwable) {
                            // initNativeMap ya maneja sus errores internamente
                        }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            } catch (e: Throwable) {
                val err = TextView(this).apply {
                    text = "No se pudo iniciar el mapa: ${e.javaClass.simpleName}: ${e.message}"
                    setTextColor(Appearance.text(this@FloatingMapService,Color.parseColor("#999999")))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                mapContainer.addView(err)
            }
        }

        // Banner de avisos (arriba al centro, se oculta solo)
        infoBanner = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (8 * density).toInt(),
                (12 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#CC007AFF")))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = (80 * density).toInt() }
            visibility = View.GONE
        }
        mapContainer.addView(infoBanner)

        // Barra de viaje (abajo): hora de llegada · duración · distancia
        tripBar = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), (9 * density).toInt(),
                (14 * density).toInt(), (9 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.parseColor(if(Appearance.dark(this@FloatingMapService)) "#DF344256" else "#D9777D83"));setStroke((density*.6f).toInt().coerceAtLeast(1),Color.parseColor("#408D97AD"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (12 * density).toInt() }
            visibility = View.GONE
        }
        mapContainer.addView(tripBar)

        // Agarradera para cambiar el tamaño (esquina inferior derecha)
        resizeHandle = ImageView(this).apply {
            setImageResource(R.drawable.ic_scaling)
            setPadding((10 * density).toInt(), (10 * density).toInt(),
                (10 * density).toInt(), (10 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#99000000")))
            }
            val s = (44 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = (10 * density).toInt()
                bottomMargin = (10 * density).toInt()
            }
        }

        root.addView(handleBar)
        root.addView(mapContainer)
        root.addView(resizeHandle)

        // Arrastrar desde la etiqueta de la barra superior
        var lastX = 0f
        var lastY = 0f
        var dragging = false
        var downX = 0f
        var downY = 0f
        handleLabel.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    lastX = e.rawX; lastY = e.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        Math.hypot((e.rawX - downX).toDouble(), (e.rawY - downY).toDouble()) > 10
                    ) dragging = true
                    if (dragging) {
                        params.x += (e.rawX - lastX).toInt()
                        params.y += (e.rawY - lastY).toInt()
                        lastX = e.rawX; lastY = e.rawY
                        wm.updateViewLayout(root, params)
                    }
                    true
                }
                else -> false
            }
        }

        // Cambiar el tamaño desde la esquina inferior derecha
        var rsW = 0
        var rsH = 0
        var rsX = 0f
        var rsY = 0f
        resizeHandle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    rsW = params.width; rsH = params.height
                    rsX = e.rawX; rsY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val minW = (220 * density).toInt()
                    val minH = (200 * density).toInt()
                    params.width = (rsW + (e.rawX - rsX)).toInt().coerceAtLeast(minW)
                    params.height = (rsH + (e.rawY - rsY)).toInt().coerceAtLeast(minH)
                    wm.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        try {
            root.visibility = View.INVISIBLE
            wm.addView(root, params)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Activa 'Mostrar sobre otras apps' para DriveFly",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        // Botón flotante: mi ubicación (esquina inferior izquierda del mapa)
        val locBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_locate)
            val s = (44 * density).toInt()
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.WHITE))
                setStroke((1 * density).toInt(), Appearance.background(this@FloatingMapService,Color.parseColor("#e0e0e0")))
            }
            elevation = 4 * density
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = (16 * density).toInt()
                leftMargin = (16 * density).toInt()
            }
            setOnClickListener { recenterOnUser() }
        }
        root.addView(locBtn)

        // Botón flotante: quitar destino (solo visible cuando hay destino)
        clearDestBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_x)
            val s = (44 * density).toInt()
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#FF3B30")))
            }
            elevation = 4 * density
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = (68 * density).toInt()
                rightMargin = (16 * density).toInt()
            }
            visibility = if (destLat != null) View.VISIBLE else View.GONE
            setOnClickListener { clearDestination() }
        }
        root.addView(clearDestBtn)

        // Ojo flotante pequeño: solo se ve cuando el mapa está oculto
        val restoreParams = WindowManager.LayoutParams(
            (48 * density).toInt(), (48 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 640
        }
        val restore = ImageView(this).apply {
            setImageResource(R.drawable.ic_map_show)
            contentDescription = "Mostrar mapa flotante"
            val pad = (11 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#DD007AFF")))
            }
            visibility = View.GONE
        }
        var rsDownX = 0f
        var rsDownY = 0f
        var rsLastX = 0f
        var rsLastY = 0f
        var rsDragging = false
        var rsDownT = 0L
        // Zona X roja para cerrar: aparece abajo cuando se arrastra el botón de mapa
        val closeZoneParams = WindowManager.LayoutParams(
            (72 * density).toInt(), (72 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }
        val closeZone = ImageView(this).apply {
            setImageResource(R.drawable.ic_x)
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Appearance.background(this@FloatingMapService,Color.parseColor("#FF3B30")))
            }
            visibility = View.GONE
        }
        closeZoneView = closeZone

        restore.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    rsDownX = e.rawX; rsDownY = e.rawY
                    rsLastX = e.rawX; rsLastY = e.rawY
                    rsDragging = false
                    rsDownT = SystemClock.uptimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!rsDragging &&
                        Math.hypot((e.rawX - rsDownX).toDouble(), (e.rawY - rsDownY).toDouble()) > 10
                    ) {
                        rsDragging = true
                        // Mostrar la zona X al empezar a arrastrar
                        try {
                            if (closeZone.parent == null) wm.addView(closeZone, closeZoneParams)
                            closeZone.visibility = View.VISIBLE
                        } catch (_: Exception) {
                        }
                    }
                    if (rsDragging) {
                        restoreParams.x += (e.rawX - rsLastX).toInt()
                        restoreParams.y += (e.rawY - rsLastY).toInt()
                        rsLastX = e.rawX; rsLastY = e.rawY
                        wm.updateViewLayout(restore, restoreParams)
                        // Resaltar la X si el ojo está sobre ella
                        try {
                            val screenH = resources.displayMetrics.heightPixels
                            val eyeCenterY = restoreParams.y + (24 * density).toInt()
                            val closeTop = screenH - (48 * density).toInt() - (72 * density).toInt()
                            val overClose = eyeCenterY > closeTop
                            closeZone.alpha = if (overClose) 1.0f else 0.6f
                            closeZone.scaleX = if (overClose) 1.2f else 1.0f
                            closeZone.scaleY = if (overClose) 1.2f else 1.0f
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Ocultar la zona X
                    try { closeZone.visibility = View.GONE } catch (_: Exception) {
                    }
                    if (!rsDragging && SystemClock.uptimeMillis() - rsDownT < 400) {
                        toggleCollapse()
                    } else if (rsDragging) {
                        // ¿Se soltó sobre la X? Cerrar todo
                        try {
                            val screenH = resources.displayMetrics.heightPixels
                            val eyeCenterY = restoreParams.y + (24 * density).toInt()
                            val closeTop = screenH - (48 * density).toInt() - (72 * density).toInt()
                            if (eyeCenterY > closeTop) {
                                stopSelf()
                                return@setOnTouchListener true
                            }
                        } catch (_: Exception) {
                        }
                        // Pegado automático al borde más cercano (izquierda o derecha) con animación suave
                        try {
                            val screenW = resources.displayMetrics.widthPixels
                            val eyeCenterX = restoreParams.x + (24 * density).toInt()
                            val targetX = if (eyeCenterX < screenW / 2) 0
                            else screenW - (48 * density).toInt()
                            val startX = restoreParams.x
                            // Animación suave de 250ms
                            val animator = android.animation.ValueAnimator.ofInt(startX, targetX)
                            animator.duration = 250
                            animator.interpolator = android.view.animation.DecelerateInterpolator()
                            animator.addUpdateListener { anim ->
                                try {
                                    restoreParams.x = anim.animatedValue as Int
                                    wm.updateViewLayout(restore, restoreParams)
                                } catch (_: Exception) {
                                }
                            }
                            animator.start()
                        } catch (_: Exception) {
                        }
                    }
                    rsDragging = false
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(restore, restoreParams)
        } catch (_: Exception) {
        }
        restoreBtn = restore

        val wazeFilter = IntentFilter().apply {
            addAction(DasherAutomation.ORDER_VOICE)
            addAction(NavigationColors.ACTION)
            addAction(MapExperience.ACTION)
            addAction(Appearance.ACTION)
            addAction(NavNotificationService.ACTION_WAZE_INFO)
            addAction("com.hirolifecreative.floatingmap.DASHER_SCAN_STATUS")
            addAction(NavNotificationService.ACTION_DESTINATION)
            addAction(NavNotificationService.ACTION_WAZE_CLEARED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, wazeReceiver, wazeFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            mapView?.onStart()
        } catch (_: Exception) {
        }
        startLocation()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Cargar preferencias de las nuevas funciones v25
        try {
            val prefs = getSharedPreferences("mapa_flotante", MODE_PRIVATE)
            speedAlertEnabled = prefs.getBoolean("speed_alert", false)
            autoSizeEnabled = prefs.getBoolean("auto_size", false)
            hudMode = prefs.getBoolean("hud_mode", false)
            if (hudMode) {
                // Aplicar modo espejo cuando el mapa esté listo
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    setHudMode(true)
                }, 2000)
            }
            if (autoSizeEnabled) {
                startFgAppChecker()
            }
        } catch (_: Exception) {
        }
    } // fin onCreateInternal

    // ================= Mapa nativo =================

    private val nativePuckProvider = DriveFlyLocationProvider()

    private fun initNativeMap(mv: MapView) {
        DriveFlyMap.followNavigationPuck(mv,{ styleReady && followCamera }) { point ->
            val now = SystemClock.uptimeMillis()
            if (styleReady && now-lastRouteRender >= 50) { lastRouteRender = now;updateRemainingRoute(point) }
        }
        mv.gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
            override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) { followCamera = false }
            override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector): Boolean = false
            override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) { }
        })
        // Toque largo en el mapa → marca ese punto como destino
        try {
            mv.gestures.addOnMapLongClickListener { point ->
                onMapLongPress(point.latitude(), point.longitude())
                true
            }
        } catch (_: Exception) {
        }

        loadNativeStyle(mv)
    }

    private fun loadNativeStyle(mv: MapView) {
        val previousCamera = mv.mapboxMap.cameraState
        val camera = CameraOptions.Builder().center(previousCamera.center).zoom(previousCamera.zoom).pitch(previousCamera.pitch).bearing(previousCamera.bearing).padding(previousCamera.padding).build()
        val wasReady = styleReady
        styleReady = false
        try {
            DriveFlyMap.load(mv) { style ->
                try {
                // Imágenes
                DriveFlyMap.puck(mv, nativePuckProvider)
                ReferenceNavigationOverlay.visibilityListener(mv) { visible -> if(NavigationSession.state!=null) infoBanner?.visibility=if(!NavigationSession.state?.message.isNullOrBlank() || (!visible && NavigationSession.nearManeuver())) View.VISIBLE else View.GONE }
                style.addImage("dest-pin", makePinBitmap())
                style.addImage("signal", makeSignalBitmap())
                style.addImage("stop", makeStopBitmap())

                // Fuentes (DSL v11)
                val emptyFc = FeatureCollection.fromFeatures(emptyList())
                style.addSource(geoJsonSource("puck-source") { featureCollection(emptyFc) })
                style.addSource(geoJsonSource("route-source") { featureCollection(emptyFc) })
                style.addSource(geoJsonSource("alternatives-source") { featureCollection(emptyFc) })
                style.addLayer(lineLayer("alternative-lines","alternatives-source") {
                    slot("middle");lineColor(if(Appearance.dark(this@FloatingMapService)) "#71B4DA" else "#AEA2F5");lineWidth(Expression.fromRaw(NavigationZoomScale.expression(7.0)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND)
                })
                style.addSource(geoJsonSource("dest-source") { featureCollection(emptyFc) })
                style.addSource(geoJsonSource("signals-source") { featureCollection(emptyFc) })
                style.addSource(geoJsonSource("stops-source") { featureCollection(emptyFc) })

                // Ruta azul uniforme en ambos temas.
                style.addLayer(lineLayer("route-casing", "route-source") {
                    slot("middle")
                    lineColor(RouteColors.casing(this@FloatingMapService));lineEmissiveStrength(1.0)
                    lineOpacity(1.0)
                    lineWidth(Expression.fromRaw(NavigationZoomScale.expression(20.0)))
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                })
                style.addLayer(lineLayer("route-line", "route-source") {
                    slot("middle")
                    lineColor(RouteColors.line(this@FloatingMapService));lineEmissiveStrength(1.0)
                    lineWidth(Expression.fromRaw(NavigationZoomScale.expression(15.0)))
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                })

                style.addLayer(lineLayer("route-highlight","route-source") { slot("middle");lineColor(RouteColors.line(this@FloatingMapService));lineEmissiveStrength(1.0);lineWidth(Expression.fromRaw(NavigationZoomScale.expression(1.2)));lineOffset(Expression.fromRaw(NavigationZoomScale.expression(-5.5)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })

                // Pin del destino
                style.addLayer(symbolLayer("dest-layer", "dest-source") {
                    iconImage("dest-pin");iconOpacity(0.0)
                    iconAllowOverlap(true)
                    iconIgnorePlacement(true)
                    iconAnchor(IconAnchor.BOTTOM)
                    iconSize(0.9)
                })

                // Edificios 3D (debajo de las etiquetas)
                // Shared DriveFlyMap supplies the 3D buildings.

                DriveFlyMap.raisePuck(mv,"route-line")
                styleReady = true
                renderSharedRoute(NavigationSession.state)
                // Logo de Mapbox más pequeño (escala 0.6)
                try {
                    mv.post {
                        try {
                            // Buscar la vista del logo (un ImageView) y escalarla
                            for (i in 0 until mv.childCount) {
                                val child = mv.getChildAt(i)
                                if (child is android.widget.ImageView) {
                                    child.scaleX = 0.6f
                                    child.scaleY = 0.6f
                                    break
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
                // Cámara inicial: última ubicación conocida o Miami
                val start = lastKnownPoint() ?: Point.fromLngLat(-80.1918, 25.7617)
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(start).zoom(15.0).pitch(60.0).build()
                )
                if (wasReady) mv.mapboxMap.setCamera(camera)
                cameraInit = true
                val l = lastGoodLoc ?: currentLoc
                if (l != null) {
                    onLocationUpdate(l)
                    RoadSigns.update(l)
                }
            } catch (e: Exception) {
                showBanner("Error al cargar el estilo: ${e.message}")
            }
        }
        } catch (e: Throwable) {
            showBanner("No se pudo cargar el mapa: ${e.javaClass.simpleName}")
        }
    }

    /** Edificios extruidos en 3D sobre el estilo claro */
    private fun add3DBuildings(style: Style) {
        try {
            val layer = fillExtrusionLayer("3d-buildings", "composite") {
                sourceLayer("building")
                minZoom(14.5)
                filter(Expression.eq(Expression.get(Expression.literal("extrude")), Expression.literal("true")))
                fillExtrusionColor(Expression.rgb(Expression.literal(200.0), Expression.literal(200.0), Expression.literal(200.0)))
                fillExtrusionHeight(Expression.get(Expression.literal("height")))
                fillExtrusionBase(Expression.get(Expression.literal("min_height")))
                fillExtrusionOpacity(0.55)
            }
            // Debajo de la primera capa de etiquetas para no tapar nombres
            var labelId: String? = null
            for (l in style.styleLayers) {
                if (l.type == "symbol") {
                    labelId = l.id
                    break
                }
            }
            if (labelId != null) style.addLayerBelow(layer, labelId!!)
            else style.addLayer(layer)
        } catch (_: Exception) {
        }
    }

    // ================= Ubicación / puck / cámara =================

    private fun onLocationUpdate(loc: Location) {
        resolveDasherAddress()
        NavigationSession.onLocation(loc)
        updateSharedProgress()
        val speedMs = if (loc.hasSpeed()) loc.speed else 0f
        updatePuck(loc, speedMs)
        updateCamera(loc, speedMs)
        RoadSigns.update(loc)
        checkSpeedLimit(loc)
        // Actualizar límite cada ~30 segundos
        val now = System.currentTimeMillis()
        if (now - lastSpeedLimitFetch > 30000) {
            lastSpeedLimitFetch = now
            fetchSpeedLimit(loc.latitude, loc.longitude)
        }
    }

    private fun updatePuck(loc: Location, speedMs: Float) {
        nativePuckProvider.update(loc)
    }

    private fun updateCamera(loc: Location, speedMs: Float) {
        if (!followCamera || NavigationSession.state != null) return
        val mv = mapView ?: return
        if (!styleReady) return
        try {
            mv.mapboxMap.easeTo(NavigationCameraProfile.options(mv,NavigationSession.latest ?: loc,false),MapAnimationOptions.Builder().duration(1100).build())
            cameraInit = true
        } catch (_: Exception) { }
    }

    /** Suavizado angular: evita giros bruscos de la cámara */
    private fun smoothBearing(newB: Double): Double {
        if (smoothB < 0) {
            smoothB = newB
            return newB
        }
        val d = (newB - smoothB + 540.0) % 360.0 - 180.0
        smoothB = (smoothB + d * 0.35 + 360.0) % 360.0
        return smoothB
    }

    private fun metersPerPixel(lat: Double, zoom: Double): Double {
        return 156543.03392 * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
    }

    private fun lastKnownPoint(): Point? {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (l != null) Point.fromLngLat(l.longitude, l.latitude) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun startLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val looper = Looper.getMainLooper()
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000, 1f, locationListener, looper
            )
        } catch (_: Exception) {
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 1000, 1f, locationListener, looper
            )
        } catch (_: Exception) {
        }
        try {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) {
                lastGoodLoc = last
                currentLoc = last
                onLocationUpdate(last)
            }
        } catch (_: Exception) {
        }
    }

    // ================= Destino y ruta =================

    private fun onMapLongPress(lat: Double, lon: Double) {
        Thread {
            val name = reverseGeocode(lat, lon) ?: "Punto en el mapa"
            try {
                val svc = Intent(this@FloatingMapService, FloatingMapService::class.java)
                    .setAction(ACTION_DESTINATION)
                    .putExtra("lat", lat)
                    .putExtra("lon", lon)
                    .putExtra("name", name)
                startService(svc)
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MapaFlotante/1.0 (uso personal)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val disp = JSONObject(body).optString("display_name", "")
            if (disp.isBlank()) null
            else disp.split(",").take(2).joinToString(",").trim().take(80)
        } catch (_: Exception) {
            null
        }
    }

    /** Pin del destino + ruta desde tu ubicación (OSRM, gratuito) */
    private fun showDestination(lat: Double,lon: Double,name: String,vehicleLat: Double=lat,vehicleLon: Double=lon,entrance: Boolean=false,merchant: String="",unit: String="") {
        NavigationSession.start(this,lat,lon,name,fix=currentLoc,vehicleLat=vehicleLat,vehicleLon=vehicleLon,entrance=entrance,merchant=merchant,unit=unit)
    }
    private fun renderSharedRoute(state: NavigationSession.State?) {
        // Android propagates this flag from whichever overlay is currently visible.
        updateScreenAwake()
        mapView?.let { DestinationMarker.set(it,state?.let { s -> Point.fromLngLat(s.lon,s.lat) },compact=true) }
        mapView?.let { DriveFlyMap.refreshTracking(it) }
        if (state == null) {
            if (lastRouteWasDasher) finishedDasherKey = dasherKey
            lastRouteWasDasher = false
            val hadDestination = destLat != null || displayedRouteJson != null
            destLat = null;destLon = null;displayedRouteJson = null;displayedInstruction = ""
            if (hadDestination) followCamera = false
            clearDestBtn?.visibility = View.GONE
            wazeBtn.visibility = View.GONE
            clearNav()
            if (NavigationSession.arrived) showBanner("Llegaste a tu destino")
            return
        }
        lastRouteWasDasher = state.name.startsWith("Dasher ·")
        if (destLat != state.lat || destLon != state.lon) followCamera = true
        destLat = state.lat;destLon = state.lon
        clearDestBtn?.visibility = View.VISIBLE
        if (state.message.isNotBlank()) showBanner(state.message)
        val mv = mapView
        if (mv != null && styleReady) {
            mv.mapboxMap.getStyle { style ->
                (style.getSource("dest-source") as? GeoJsonSource)?.feature(Feature.fromGeometry(Point.fromLngLat(state.lon,state.lat)))
                val route = state.route
                val remaining = NavigationSession.remainingCoordinates()
                val activeFeatures = if (remaining.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(remaining.map { p -> Point.fromLngLat(p.longitude,p.latitude) }))) else emptyList()
                (style.getSource("route-source") as? GeoJsonSource)?.featureCollection(FeatureCollection.fromFeatures(activeFeatures))
                val alternatives = state.options.filter { it.route.coordinates != route?.route?.coordinates }.map {
                    Feature.fromGeometry(LineString.fromLngLats(it.route.coordinates.map { p -> Point.fromLngLat(p.longitude,p.latitude) }))
                }
                (style.getSource("alternatives-source") as? GeoJsonSource)?.featureCollection(FeatureCollection.fromFeatures(alternatives))
            }
            displayedRouteJson = state.route?.json
        }
        updateSharedProgress()
        currentLoc?.let { updateCamera(it,if (it.hasSpeed()) it.speed else 0f) }
    }
    private fun updateRemainingRoute(point: Point) {
        val coordinates = NavigationSession.remainingCoordinates(point.longitude(),point.latitude())
        val features = if (coordinates.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates.map { Point.fromLngLat(it.longitude,it.latitude) }))) else emptyList()
        mapView?.mapboxMap?.getStyle { style ->
            (style.getSource("route-source") as? GeoJsonSource)?.featureCollection(FeatureCollection.fromFeatures(features))
        }
    }
    private fun updateSharedProgress() {
        val state = NavigationSession.state ?: return
        val route = state.route ?: return
        val progress = NavigationSession.progress
        val step = progress?.step ?: if (route.route.steps.size > 1) 1 else 0
        val instruction = state.message.ifBlank { if(NavigationSession.nearManeuver()) route.route.steps.getOrNull(step)?.instruction.orEmpty() else "" }
        if(instruction.isBlank() && displayedInstruction.isNotBlank()) {
            if(infoBanner?.text?.toString()==displayedInstruction) infoBanner?.visibility=View.GONE
            displayedInstruction=""
        }
        if (instruction.isNotBlank() && instruction != displayedInstruction) { displayedInstruction = instruction;showBanner(instruction) }
        val seconds = progress?.seconds ?: route.duration
        val meters = progress?.remaining ?: route.distance
        val arrival = System.currentTimeMillis()+(seconds*1000).toLong()
        val eta = java.text.SimpleDateFormat("h:mm",java.util.Locale.US).format(java.util.Date(arrival))
        val arrivalText="$eta ETA";val durationText="${kotlin.math.ceil(seconds/60).toInt()} min";val distanceText="${"%.1f".format(meters/1609.34)} mi"
        tripBar?.text=android.text.SpannableString("$arrivalText   $durationText   $distanceText").apply { setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#56DB5C")),arrivalText.length+3,arrivalText.length+3+durationText.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        tripBar?.contentDescription=tripBar?.text.toString()+if(route.trafficAware) "" else ". Sin tráfico en vivo"
        tripBar?.visibility = View.VISIBLE
    }

    private fun clearNav() {
        mapView?.let { DestinationMarker.set(it,null,compact=true) }
        if (NavigationSession.state != null) { NavigationSession.stop();return }
        routeRequest++; pendingRoute = false
        val mv = mapView ?: return
        if (!styleReady) return
        try {
            mv.mapboxMap.getStyle { style ->
                (style.getSource("route-source") as? GeoJsonSource)
                    ?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
                (style.getSource("alternatives-source") as? GeoJsonSource)?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
                (style.getSource("dest-source") as? GeoJsonSource)
                    ?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        } catch (_: Exception) {
        }
        try {
            tripBar?.post { tripBar?.visibility = View.GONE }
        } catch (_: Exception) {
        }
    }

    /** Centra el mapa en la ubicación actual del usuario */
    private fun recenterOnUser() {
        followCamera = true
        mapView?.let { DriveFlyMap.refreshTracking(it) }
        val loc = currentLoc ?: lastGoodLoc
        if (loc == null) {
            showBanner("Esperando ubicación…")
            return
        }
        val mv = mapView ?: return
        if (!styleReady || NavigationSession.state != null) return
        try {
            val pt = Point.fromLngLat(loc.longitude, loc.latitude)
            mv.mapboxMap.setCamera(
                NavigationCameraProfile.options(mv,loc,NavigationSession.state != null)
            )
        } catch (_: Exception) {
        }
    }

    /** Quita el destino, el pin y la ruta */
    private fun clearDestination() {
        // Cancel the shared session and any lookup already running, rather than only erasing pixels.
        if (dasherKey.isNotBlank()) finishedDasherKey=dasherKey
        dasherRequest++;pendingDasherAddress=null;dasherResolvingKey=null
        dasherRetryAfter=System.currentTimeMillis()+300000
        val hadSession=NavigationSession.state!=null
        NavigationSession.stop()
        if(!hadSession) renderSharedRoute(null)
        showBanner("Destino eliminado")
    }

    /** Aplica la transparencia guardada a la ventana */
    fun applyAlpha(alpha: Float) {
        try {
            params.alpha = alpha.coerceIn(0.5f, 1.0f)
            wm.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
    }

    /** Activa/desactiva el modo espejo HUD (para reflejar en el parabrisas) */
    fun setHudMode(enabled: Boolean) {
        hudMode = enabled
        try {
            // El mapContainer es el segundo hijo del root (después del handleBar)
            val mapContainer = (root.getChildAt(1) as? FrameLayout)
            mapContainer?.scaleX = if (enabled) -1f else 1f
            showBanner(if (enabled) "Modo HUD activado" else "Modo HUD desactivado")
        } catch (_: Exception) {
        }
    }

    /** Revisa la app al frente y ajusta el tamaño automáticamente */
    private fun startFgAppChecker() {
        if (!autoSizeEnabled) return
        fgCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
        fgCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    val fgApp = getForegroundApp()
                    // Apps donde el mapa se hace compacto
                    val compactApps = setOf(
                        "com.dd.doordash",
                        "com.doordash.driverapp",
                        "com.waze"
                    )
                    val wantCompact = fgApp in compactApps
                    val d = resources.displayMetrics.density
                    val isCompact = params.width < (280 * d).toInt()
                    if (wantCompact != isCompact) {
                        val targetW = if (wantCompact) (220 * d).toInt() else (340 * d).toInt()
                        val targetH = if (wantCompact) (160 * d).toInt() else (260 * d).toInt()
                        val startW = params.width
                        val startH = params.height
                        // Animación suave de tamaño (300ms)
                        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
                        animator.duration = 300
                        animator.interpolator = android.view.animation.DecelerateInterpolator()
                        animator.addUpdateListener { anim ->
                            try {
                                val f = anim.animatedValue as Float
                                params.width = (startW + (targetW - startW) * f).toInt()
                                params.height = (startH + (targetH - startH) * f).toInt()
                                wm.updateViewLayout(root, params)
                            } catch (_: Exception) {
                            }
                        }
                        animator.start()
                    }
                } catch (_: Exception) {
                }
                fgCheckHandler?.postDelayed(this, 3000)
            }
        }
        fgCheckHandler?.post(fgCheckRunnable!!)
    }

    private fun getForegroundApp(): String? {
        return try {
            val usm = getSystemService(android.app.usage.UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                now - 10000, now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /** Alerta sonora si excedes el límite de velocidad */
    private fun checkSpeedLimit(loc: android.location.Location) {
        if (!speedAlertEnabled) return
        val limit = lastSpeedLimit ?: return
        if (!loc.hasSpeed()) return
        val mph = (loc.speed * 2.23694f).toInt()
        if (mph > limit + 5) {
            val now = System.currentTimeMillis()
            if (now - lastSpeedAlertTime > 10000) { // máximo cada 10 segundos
                lastSpeedAlertTime = now
                try {
                    val tone = android.media.ToneGenerator(
                        android.media.AudioManager.STREAM_NOTIFICATION, 80
                    )
                    tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                    showBanner("⚠ Excedes el límite: $mph mph (máx $limit)")
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Consulta el límite de velocidad de la vía actual (OSM/Overpass) */
    private fun fetchSpeedLimit(lat: Double, lon: Double) {
        Thread {
            try {
                val query = """
                    [out:json][timeout:10];
                    way(around:30,$lat,$lon)["maxspeed"];
                    out tags 1;
                """.trimIndent()
                val url = java.net.URL("https://overpass-api.de/api/interpreter")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.outputStream.use { it.write(query.toByteArray()) }
                val txt = conn.inputStream.bufferedReader().readText()
                // Buscar "maxspeed":"50" o "maxspeed":"30 mph"
                val m = """"maxspeed"\s*:\s*"([^"]+)"""".toRegex().find(txt)
                if (m != null) {
                    val raw = m.groupValues[1]
                    // Convertir a mph (OSM usa km/h por defecto, mph si dice "mph")
                    val num = """(\d+)""".toRegex().find(raw)?.groupValues?.get(1)?.toIntOrNull()
                    if (num != null) {
                        lastSpeedLimit = if (raw.contains("mph")) num
                        else (num * 0.621371).toInt()
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun fetchRoute(dLat: Double,dLon: Double) {
        pendingRoute = false
        NavigationSession.onLocation(currentLoc ?: return)
        if (NavigationSession.state?.route == null) NavigationSession.request()
    }

    // ================= Semáforos y stops (OpenStreetMap) =================

    private fun maybeRefreshSignals(loc: Location) {
        val last = lastSignalCenter
        if (last != null && loc.distanceTo(last) < 500) return
        if (signalsInFlight || !styleReady) return
        lastSignalCenter = loc
        fetchSignals(loc.latitude, loc.longitude)
    }

    private fun fetchSignals(lat: Double, lon: Double) {
        signalsInFlight = true
        Thread {
            try {
                val query = "[out:json][timeout:12];" +
                        "(node[\"highway\"=\"traffic_signals\"](around:700,$lat,$lon);" +
                        "node[\"highway\"=\"stop\"](around:700,$lat,$lon););out body;"
                val conn = URL("https://overpass-api.de/api/interpreter")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("User-Agent", "MapaFlotante/1.0 (uso personal)")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.bufferedWriter().use { it.write("data=$query") }
                val body = conn.inputStream.bufferedReader().readText()
                val elements = JSONObject(body).getJSONArray("elements")
                val sigFeats = ArrayList<Feature>()
                val stopFeats = ArrayList<Feature>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    if (el.optString("type") != "node") continue
                    val elLat = el.optDouble("lat", 0.0)
                    val elLon = el.optDouble("lon", 0.0)
                    if (elLat == 0.0 && elLon == 0.0) continue
                    val tags = el.optJSONObject("tags")
                    val hw = tags?.optString("highway", "") ?: ""
                    val pt = Point.fromLngLat(elLon, elLat)
                    when (hw) {
                        "traffic_signals" -> sigFeats.add(Feature.fromGeometry(pt))
                        "stop" -> stopFeats.add(Feature.fromGeometry(pt))
                    }
                }
                val mv = mapView
                if (mv != null) {
                    try {
                        mv.mapboxMap.getStyle { style ->
                            (style.getSource("signals-source") as? GeoJsonSource)
                                ?.featureCollection(FeatureCollection.fromFeatures(sigFeats))
                            (style.getSource("stops-source") as? GeoJsonSource)
                                ?.featureCollection(FeatureCollection.fromFeatures(stopFeats))
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                signalsInFlight = false
            }
        }.start()
    }

    // ================= Bitmaps dibujados en código =================

    /** Puck: halo blanco + disco azul + flecha blanca (apunta arriba) */
    private fun makePuckBitmap(): Bitmap {
        val s = 132
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = s / 2f
        val cy = s / 2f
        val p = Paint().apply { isAntiAlias = true }
        p.color = Color.WHITE
        c.drawCircle(cx, cy, 60f, p)
        p.color = Color.parseColor("#007AFF")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawCircle(cx, cy, 60f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 45f, p)
        val arrow = Path().apply {
            moveTo(cx, cy - 30f)
            lineTo(cx + 21f, cy + 17f)
            lineTo(cx, cy + 7f)
            lineTo(cx - 21f, cy + 17f)
            close()
        }
        p.color = Color.WHITE
        c.drawPath(arrow, p)
        return b
    }

    /** Pin rojo de destino */
    private fun makePinBitmap(): Bitmap {
        val w = 96
        val h = 128
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint().apply { isAntiAlias = true }
        val cx = w / 2f
        p.color = Color.parseColor("#FF3B30")
        c.drawCircle(cx, 44f, 34f, p)
        val tri = Path().apply {
            moveTo(cx - 28f, 62f)
            lineTo(cx + 28f, 62f)
            lineTo(cx, h - 8f)
            close()
        }
        c.drawPath(tri, p)
        p.color = Color.WHITE
        c.drawCircle(cx, 44f, 14f, p)
        return b
    }

    /** Semáforo con sus 3 luces */
    private fun makeSignalBitmap(): Bitmap {
        val w = 52
        val h = 104
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint().apply { isAntiAlias = true }
        p.color = Color.parseColor("#222222")
        c.drawRoundRect(RectF(2f, 2f, w - 2f, h - 2f), 14f, 14f, p)
        val cx = w / 2f
        val lights = listOf(
            Pair(24f, "#ff3b30"),
            Pair(52f, "#ffcc00"),
            Pair(80f, "#34c759")
        )
        for ((cy, col) in lights) {
            p.color = Color.parseColor(col)
            c.drawCircle(cx, cy, 13f, p)
        }
        return b
    }

    /** Señal de STOP octagonal */
    private fun makeStopBitmap(): Bitmap {
        val s = 96
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint().apply { isAntiAlias = true }
        val cx = s / 2f
        val cy = s / 2f
        val r = 44f
        val oct = Path()
        for (i in 0 until 8) {
            val a = Math.toRadians(22.5 + i * 45.0)
            val x = cx + r * cos(a)
            val y = cy + r * Math.sin(a)
            if (i == 0) oct.moveTo(x.toFloat(), y.toFloat())
            else oct.lineTo(x.toFloat(), y.toFloat())
        }
        oct.close()
        p.color = Color.parseColor("#d32f2f")
        c.drawPath(oct, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = Color.WHITE
        val oct2 = Path()
        for (i in 0 until 8) {
            val a = Math.toRadians(22.5 + i * 45.0)
            val x = cx + (r - 6f) * cos(a)
            val y = cy + (r - 6f) * Math.sin(a)
            if (i == 0) oct2.moveTo(x.toFloat(), y.toFloat())
            else oct2.lineTo(x.toFloat(), y.toFloat())
        }
        oct2.close()
        c.drawPath(oct2, p)
        p.style = Paint.Style.FILL
        p.textSize = 22f
        p.textAlign = Paint.Align.CENTER
        val fm = p.fontMetrics
        c.drawText("STOP", cx, cy - (fm.ascent + fm.descent) / 2, p)
        return b
    }

    // ================= Ventana: ocultar / mostrar =================

    private fun showBanner(text: String) {
        try {
            infoBanner?.post {
                bannerHide?.let { infoBanner?.removeCallbacks(it) }
                infoBanner?.text = text
                infoBanner?.visibility = if(mapView?.let { ReferenceNavigationOverlay.hasBubble(it) }==true && NavigationSession.state?.message.isNullOrBlank()) View.GONE else View.VISIBLE
                val hide = Runnable {
                    try {
                        val active = NavigationSession.state
                        val guidance = active?.message?.takeIf { it.isNotBlank() } ?: displayedInstruction
                        if (active != null && guidance.isNotBlank()) {
                            infoBanner?.text = guidance
                            infoBanner?.visibility = if(mapView?.let { ReferenceNavigationOverlay.hasBubble(it) }==true && NavigationSession.state?.message.isNullOrBlank()) View.GONE else View.VISIBLE
                        } else infoBanner?.visibility = View.GONE
                    } catch (_: Exception) {
                    }
                }
                bannerHide = hide
                infoBanner?.postDelayed(hide, 5000)
            }
        } catch (_: Exception) {
        }
    }

    /** Oculta/muestra la ventana con animación suave: solo queda el botón de mapa */
    private var firstCommandHandled = false
    private var openAfterCollapse = false

    private fun updateScreenAwake() {
        val destinationActive = NavigationSession.state != null
        if (::root.isInitialized) root.keepScreenOn = destinationActive || (firstCommandHandled && !collapsed)
        restoreBtn?.keepScreenOn = destinationActive
    }

    private fun openResolvedPickup() {
        if (!collapsed) return
        if (collapseAnimating) { openAfterCollapse = true; return }
        toggleCollapse()
    }

    private fun toggleCollapse() {
        if (collapseAnimating) return
        collapseAnimating = true
        val smooth = android.view.animation.DecelerateInterpolator(1.5f)
        if (!collapsed) {
            collapsed = true
            updateScreenAwake()
            root.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(280)
                .setInterpolator(smooth)
                .withEndAction {
                    root.visibility = View.GONE
                    collapseAnimating = false
                    root.alpha = 1f
                    root.scaleX = 1f
                    root.scaleY = 1f
                    restoreBtn?.let {
                        it.visibility = View.VISIBLE
                        it.alpha = 0f
                        it.scaleX = 0.7f
                        it.scaleY = 0.7f
                        it.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(280).setInterpolator(smooth).start()
                    }
                    if (openAfterCollapse) { openAfterCollapse = false; openResolvedPickup() }
                }.start()
        } else {
            collapsed = false
            updateScreenAwake()
            restoreBtn?.let {
                it.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(220)
                    .setInterpolator(smooth)
                    .withEndAction { it.visibility = View.GONE }.start()
            }
            root.alpha = 0f
            root.scaleX = 0.92f
            root.scaleY = 0.92f
            root.visibility = View.VISIBLE
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
                .setInterpolator(smooth).withEndAction { collapseAnimating = false }.start()
        }
    }

    /** Abre el destino en Waze (deep link oficial) */
    private fun openInWaze() {
        val lat = destLat
        val lon = destLon
        if (lat == null || lon == null) {
            Toast.makeText(this, "Primero marca un destino en el mapa", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(
                Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(this, "Abriendo Waze…", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {
        }
        try {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(web)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir Waze", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAsForeground() {
        val chId = "floatingmap"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chId, "DriveFly", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingMapService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, chId)
            .setContentTitle("Mapa flotante activo")
            .setContentText("Tu ubicación en vivo sobre otras apps")
            .setSmallIcon(R.drawable.ic_launcher)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_launcher),
                    "Ocultar", stopIntent
                ).build()
            )
            .setOngoing(true)
            .build()
        startForeground(1, notif)
        isRunning = true
    }

    private fun dasherStatus(message: String) {
        if (NavigationSession.state == null) showBanner(message)
        getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putString("dasher_status",message).apply()
        if (NavigationSession.state == null) {
            tripBar?.text = message
            tripBar?.visibility = View.VISIBLE
        }
    }

    /** Geocode inside the foreground location process, using its live fix and token. */
    private fun acceptDasherAddress(address: String,stage: String,merchant: String="",unit: String="") {
        if (address.isBlank()) return
        val key = address.lowercase(java.util.Locale.ROOT)+"|"+stage
        if (key == finishedDasherKey) return
        if(key!=dasherKey) { pendingDasherMerchant=merchant;pendingDasherUnit=unit }
        else { if(merchant.isNotBlank()) pendingDasherMerchant=merchant;if(unit.isNotBlank()) pendingDasherUnit=unit }
        NavigationSession.updateSpeechDetails("Dasher · $stage: $address",pendingDasherMerchant,pendingDasherUnit)
        finishedDasherKey = ""
        val now = System.currentTimeMillis()
        if (key == dasherKey && (pendingDasherAddress != null || dasherResolvingKey == key || now < dasherRetryAfter)) return
        dasherRequest++;dasherKey = key;dasherAttempt = now;dasherRetryAfter = now+300000
        pendingDasherAddress = address to stage
        dasherStatus("Dasher · dirección de ${stage.lowercase()} detectada")
        resolveDasherAddress()
    }

    private fun resolveDasherAddress() {
        val pending = pendingDasherAddress ?: return
        val loc = currentLoc ?: NavigationSession.latest ?: lastGoodLoc
        if (loc == null || System.currentTimeMillis()-loc.time > 120000) {
            dasherStatus("Dasher · esperando ubicación GPS")
            return
        }
        pendingDasherAddress = null
        val request = dasherRequest
        val resolvingKey=dasherKey
        dasherResolvingKey=resolvingKey
        val token = getSharedPreferences("mapa_flotante",MODE_PRIVATE).getString("mapbox_token","").orEmpty()
        dasherStatus("Dasher · buscando dirección de ${pending.second.lowercase()}")
        Thread {
            try {
                val coordinate = DasherGeocoder.find(pending.first,token,DasherGeocoder.Coordinate(loc.latitude,loc.longitude),MapExperience.read(this).entrances)
                dasherHandler.post {
                    if (request != dasherRequest || !isRunning) return@post
                    dasherResolvingKey=null
                    if (coordinate == null) {
                        dasherRetryAfter=System.currentTimeMillis()+60000
                        DasherLookupLog.record(this@FloatingMapService,"Mapbox no devolvió una coincidencia única; reintento en 1 minuto")
                        dasherStatus("Dasher · el destino no tuvo una coincidencia única en Mapbox. Reintento en 1 min; puedes usar Indicaciones → DriveFly")
                    } else {
                        DasherLookupLog.record(this@FloatingMapService,"Destino de ${pending.second.lowercase()} localizado automáticamente")
                        showDestination(coordinate.lat,coordinate.lon,"Dasher · ${pending.second}: ${pending.first}",coordinate.vehicleLat ?: coordinate.lat,coordinate.vehicleLon ?: coordinate.lon,coordinate.entrance,pendingDasherMerchant,pendingDasherUnit)
                        if (pending.second == "Recogida") openResolvedPickup()
                    }
                }
            } catch (problem: Exception) {
                dasherHandler.post {
                    if (request == dasherRequest && isRunning) {
                        dasherResolvingKey=null
                        dasherRetryAfter=System.currentTimeMillis()+60000
                        val explanation=if(problem is DasherGeocoder.SearchFailure) problem.explanation else "Falló la conexión al buscar el destino; reintento en 1 minuto"
                        DasherLookupLog.record(this@FloatingMapService,explanation)
                        dasherStatus("Dasher · $explanation")
                    }
                }
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!firstCommandHandled && ::root.isInitialized) {
            firstCommandHandled = true
            collapsed = intent?.action == "com.hirolifecreative.floatingmap.DASHER_ADDRESS"
            root.visibility = if (collapsed) View.GONE else View.VISIBLE
            restoreBtn?.visibility = if (collapsed) View.VISIBLE else View.GONE
            updateScreenAwake()
        }
        when (intent?.action) {
            "com.hirolifecreative.floatingmap.DASHER_ADDRESS" -> {
                acceptDasherAddress(intent.getStringExtra("address").orEmpty(),intent.getStringExtra("stage") ?: "Destino",intent.getStringExtra("merchant").orEmpty(),intent.getStringExtra("unit").orEmpty())
            }
            "com.hirolifecreative.floatingmap.DASHER_SCAN_STATUS" -> {
                if (NavigationSession.state == null && pendingDasherAddress == null)
                    dasherStatus(intent.getStringExtra("status") ?: "Dasher · esperando dirección")
            }
            "com.hirolifecreative.floatingmap.DASHER_SESSION_START" -> {
                if (isRunning && ::root.isInitialized && root.isAttachedToWindow)
                    sendBroadcast(Intent("com.hirolifecreative.floatingmap.DASHER_SESSION_READY").setPackage(packageName))
            }
            "STOP" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DESTINATION -> {
                val lat = intent.getDoubleExtra("lat", 0.0)
                val lon = intent.getDoubleExtra("lon", 0.0)
                val name = intent.getStringExtra("name") ?: "Destino"
                if (lat != 0.0 || lon != 0.0) showDestination(lat,lon,name,intent.getDoubleExtra("vehicleLat",lat),intent.getDoubleExtra("vehicleLon",lon),intent.getBooleanExtra("entrance",false))
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Appearance.mode(this)=="system" && ::root.isInitialized) {
            Appearance.applyTree(root)
            mapView?.let { loadNativeStyle(it) }
        }
    }

    override fun onDestroy() {
        if (::root.isInitialized) root.keepScreenOn = false
        restoreBtn?.keepScreenOn = false
        dasherRequest++;dasherHandler.removeCallbacksAndMessages(null)
        NavigationSession.remove(sharedNavigation)
        routeRequest++; pendingRoute = false
        isRunning = false
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(wazeReceiver)
        } catch (_: Exception) {
        }
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        try {
            bannerHide?.let { infoBanner?.removeCallbacks(it) }
        } catch (_: Exception) {
        }
        try {
            mapView?.onStop()
        } catch (_: Exception) {
        }
        try {
            mapView?.onDestroy()
        } catch (_: Exception) {
        }
        mapView = null
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        try {
            restoreBtn?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        try {
            closeZoneView?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        closeZoneView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
