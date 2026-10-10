package com.hirolifecreative.floatingmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.interactions.standard.generated.StandardPoi
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * NavigationActivity — Navegador DriveFly con estructura correcta.
 *
 * Flujo:
 * 1. Abrir → Mapa + barra de búsqueda
 * 2. Buscar → Lista de resultados
 * 3. Elegir → Vista previa con ruta
 * 4. "Ir" → Navegación giro por giro con voz
 * 5. Llegar → Fin
 */
open class NavigationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var token: String = ""
    private var locationManager: LocationManager? = null
    private var currentLoc: Location? = null
    private var locationListener: LocationListener? = null
    private var searchGeneration = 0
    private var routeGeneration = 0
    private var pendingFloating = false
    private var previewInfo: TextView? = null
    private var locationControl: View? = null
    private var floatingToggle: DriveFlySwitch? = null
    private val puckProvider = DriveFlyLocationProvider()
    private var mapReady = false
    private var sharedRouteJson: String? = null
    private var lastRouteRender = 0L
    private val sharedNavigation: (NavigationSession.State?) -> Unit = { state ->
        updateScreenAwake(state != null)
        if (::mapView.isInitialized) DriveFlyMap.refreshTracking(mapView)
        threeD = NavigationSession.threeD
        (perspectiveControl as? TextView)?.text = if (threeD) "2D" else "3D"
        if (state == null) {
            if (navigating) endNav(NavigationSession.arrived)
        } else if (::previewCard.isInitialized && !isDestroyed) {
            val wasNavigating = navigating
            val changed = sharedRouteJson != state.route?.json || destPlace?.point?.latitude() != state.lat || destPlace?.point?.longitude() != state.lon
            destPlace = Place(state.name,"",Point.fromLngLat(state.lon,state.lat),vehicle=Point.fromLngLat(state.vehicleLon,state.vehicleLat),entrance=state.entrance,accessResolved=true)
            if (!wasNavigating) { hidePanels(clearSelection=false);followLocation = true }
            cachedRoute = state.route;routeOptions = state.options
            selectedRouteIndex = routeOptions.indexOfFirst { it.json == state.route?.json }.coerceAtLeast(0)
            if (changed) {
                sharedRouteJson = state.route?.json
                val parsed = state.route?.let { parseRoute(it.json) }
                routeCoords = parsed?.first.orEmpty()
                routeSteps = parsed?.second ?: listOf(RouteStep(state.message.ifBlank { "Calculando ruta…" },"",0.0,Point.fromLngLat(state.lon,state.lat)))
                stepIdx = if (routeSteps.size > 1) 1 else 0
                clearRoute();drawRoute();drawDestination()
            }
            if (!wasNavigating) startNav() else updateNavUI()
            NavigationSession.latest?.let { fix -> currentLoc = fix;if (mapReady && followLocation) DriveFlyMap.refreshTracking(mapView) }
        }
    }
    private var navSpeedText: TextView? = null
    private val speedListener: (Double?) -> Unit = { speed -> navSpeedText?.text=if(speed==null) "-- mph" else "${kotlin.math.round(speed).toInt()} mph" }
    private var colorsAtStart = NavigationColorConfig()
    private var experienceAtStart = MapExperienceConfig()
    private var recoveringStartup = false
    private val startupComplete = Runnable {
        getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putBoolean("map_start_pending",false).apply()
    }
    private var businessGeneration = 0
    private var mapPickGeneration = 0
    private var businessDetailsGeneration = 0
    private var selectedCategory = "restaurant"
    private var businessLoading = false
    private var detailsLoading = false
    private var hoursFallbackCursor = 0
    private val businessCache = mutableMapOf<String,Pair<PlacesClient.Details,Long>>()
    private var businessPanel: BusinessPanel? = null
    private var businessPanelId: String? = null
    private var businessLegend: LinearLayout? = null
    private var businessLegendTitle: TextView? = null
    private var lastBrowseAttempt = 0L
    private var lastBrowsePoint: Point? = null
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() {
            if (!isDestroyed && mapReady) {
                drawBusinesses(); refreshBusinessPanel(); refreshBusinessHours()
                if (nearbyBusinesses.isEmpty() && !navigating && destPlace == null) autoExplore()
            }
            statusHandler.postDelayed(this,60000)
        }
    }
    private var routeOptions: List<RoutingClient.Result> = emptyList()
    private var selectedRouteIndex = 0
    private var routeChoices: LinearLayout? = null
    private var nearbyBusinesses: List<PlacesClient.Business> = emptyList()
    private var threeD = true
    private var followLocation = true
    private var perspectiveControl: View? = null
    private var cachedRoute: RoutingClient.Result? = null
    private var routeLoading = false
    private var startRouteButton: View? = null

    // UI principal
    private lateinit var drawer: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var root: FrameLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var quickRow: LinearLayout
    private var homeCard: LinearLayout? = null
    private var homeStatus: TextView? = null

    // Paneles
    private lateinit var resultsPanel: LinearLayout
    private lateinit var previewCard: DriveFlySheet
    private lateinit var navBanner: LinearLayout
    private lateinit var navBottomBar: LinearLayout

    // Estado
    private data class Place(val name: String, val address: String, val point: Point, val businessId: String? = null, val business: Boolean = false,val vehicle: Point?=null,val entrance: Boolean=false,val accessResolved: Boolean=false)
    private data class RouteStep(val instruction: String, val road: String, val distM: Double, val point: Point)

    private var routeSteps: List<RouteStep> = emptyList()
    private var routeCoords: List<Point> = emptyList()
    private var stepIdx = 0
    private var navigating = false
    private var destPlace: Place? = null
    private var lastRecalc = 0L

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ============ CICLO DE VIDA ============

    private var themeAtStart = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeAtStart = Appearance.dark(this)
        colorsAtStart = NavigationColors.read(this)
        CrashLog.install(this)
        DriveFlyUi.chrome(this)
        pendingFloating = savedInstanceState?.getBoolean("pending_floating") ?: false

        try {
            val prefs = getSharedPreferences("mapa_flotante", MODE_PRIVATE)
            token = prefs.getString("mapbox_token", "").orEmpty().trim()
            if (token.isEmpty()) {
                buildSetupUI()
                return
            }

            recoveringStartup = prefs.getBoolean("map_start_pending",false)
            experienceAtStart = MapExperience.read(this)
            if (recoveringStartup) {
                prefs.edit().putBoolean("advanced_map",false).putBoolean("map_start_pending",false).commit()
                MapExperience.save(this,MapExperience.read(this).copy(enabled=false))
                experienceAtStart = MapExperience.read(this)
                buildSetupUI()
                return
            }
            prefs.edit().putBoolean("map_start_pending",true).commit()
            MapboxOptions.accessToken = token

            buildUI()
            NavigationSession.observe(this,sharedNavigation)
        SpeedMotion.observe(speedListener)
            startLocation()
            statusHandler.postDelayed(startupComplete,10000)
        } catch (t: Throwable) {
            CrashLog.save(this, t)
            recoveringStartup = true
            getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putBoolean("map_start_pending",false).apply()
            buildSetupUI()
            Toast.makeText(this, "No se pudo cargar el mapa. Revisa Configuración y Diagnóstico.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateScreenAwake(active: Boolean) {
        if (active) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        updateScreenAwake(NavigationSession.state != null)
        DriveFlyUi.chrome(this)
        if (colorsAtStart != NavigationColors.read(this)) { recreate();return }
        if (themeAtStart != Appearance.dark(this)) { recreate();return }
        updateHomeCard()
        statusHandler.removeCallbacks(statusTick);statusHandler.post(statusTick)
        val currentToken = getSharedPreferences("mapa_flotante", MODE_PRIVATE).getString("mapbox_token", "").orEmpty().trim()
        if (currentToken != token || (!recoveringStartup && experienceAtStart != MapExperience.read(this))) { recreate(); return }
        if (pendingFloating && android.provider.Settings.canDrawOverlays(this) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            openFloating()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_floating", pendingFloating)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) DriveFlyUi.chrome(this)
    }

    override fun onStart() {
        super.onStart()
        try { mapView.onStart() } catch (_: Exception) {}
        if (::mapView.isInitialized && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            startLocation()
    }

    override fun onStop() {
        statusHandler.removeCallbacks(statusTick)
        statusHandler.removeCallbacks(startupComplete)
        startupComplete.run()
        super.onStop()
        try { mapView.onStop() } catch (_: Exception) {}
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
    }

    override fun onDestroy() {
        updateScreenAwake(false)
        statusHandler.removeCallbacks(statusTick)
        statusHandler.removeCallbacks(startupComplete)
        NavigationSession.remove(sharedNavigation)
        SpeedMotion.remove(speedListener)
        businessGeneration++; businessDetailsGeneration++
        super.onDestroy()
        searchGeneration++
        routeGeneration++
        locationListener?.let { locationManager?.removeUpdates(it) }
        try { mapView.onDestroy() } catch (_: Exception) {}
    }

    // ============ UI PRINCIPAL ============

    private fun buildUI() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Appearance.background(this@NavigationActivity,Color.parseColor("#e8e8e8")))
        }

        // 1. Mapa pantalla completa
        mapView = MapView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(mapView)
        DriveFlyMap.load(mapView) {
            mapReady = true
            DriveFlyMap.puck(mapView, puckProvider)
            currentLoc?.let { puckProvider.update(it) }
            drawDestination()
            drawBusinesses()
            currentLoc?.let { if (destPlace == null) centerMap(it,16.0) }
            autoExplore()
            if (routeCoords.isNotEmpty()) drawRoute()
            if (navigating) DriveFlyMap.refreshTracking(mapView)
        }
        mapView.gestures.updateSettings { rotateEnabled = true; pitchEnabled = true; scrollEnabled = true; pinchToZoomEnabled = true }
        DriveFlyMap.followNavigationPuck(mapView,{ mapReady && followLocation }) { point ->
            val now = android.os.SystemClock.uptimeMillis()
            if (mapReady && navigating && now-lastRouteRender >= 50) { lastRouteRender = now;updateRemainingRoute(point) }
        }
        mapView.gestures.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) { followLocation = false }
            override fun onMove(detector: MoveGestureDetector) = false
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        })
        mapView.gestures.addOnRotateListener(object : com.mapbox.maps.plugin.gestures.OnRotateListener {
            override fun onRotateBegin(detector: com.mapbox.android.gestures.RotateGestureDetector) { followLocation = false }
            override fun onRotate(detector: com.mapbox.android.gestures.RotateGestureDetector) {}
            override fun onRotateEnd(detector: com.mapbox.android.gestures.RotateGestureDetector) {}
        })
        mapView.gestures.addOnScaleListener(object : com.mapbox.maps.plugin.gestures.OnScaleListener {
            override fun onScaleBegin(detector: com.mapbox.android.gestures.StandardScaleGestureDetector) { followLocation = false }
            override fun onScale(detector: com.mapbox.android.gestures.StandardScaleGestureDetector) {}
            override fun onScaleEnd(detector: com.mapbox.android.gestures.StandardScaleGestureDetector) {}
        })
        mapView.gestures.addOnShoveListener(object : com.mapbox.maps.plugin.gestures.OnShoveListener {
            override fun onShoveBegin(detector: com.mapbox.android.gestures.ShoveGestureDetector) { followLocation = false }
            override fun onShove(detector: com.mapbox.android.gestures.ShoveGestureDetector) {}
            override fun onShoveEnd(detector: com.mapbox.android.gestures.ShoveGestureDetector) {}
        })
        mapView.gestures.addOnMapLongClickListener { point ->
            if (!navigating) showPreview(Place("Punto en el mapa", "%.5f, %.5f".format(java.util.Locale.US, point.latitude(), point.longitude()), point))
            true
        }
        mapView.gestures.addOnMapClickListener { point ->
            if (!navigating && !searchInput.hasFocus()) {
                pickMapPlace(point); true
            } else false
        }
        mapView.mapboxMap.setCamera(CameraOptions.Builder().pitch(CameraPreferences.current(this).tilt.toDouble()).build())
        mapView.scalebar.updateSettings { enabled = false }
        mapView.compass.updateSettings { enabled = false }

        // One compact toolbar; menu and search share a single surface.
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg("#FFFFFF", 20); elevation = dp(3).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = FrameLayout.LayoutParams(-1, dp(56)).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12)
            }
        }
        searchBar.addView(DriveFlyUi.iconButton(this, "menu", "Abrir menú de DriveFly") { drawer.openDrawer(androidx.core.view.GravityCompat.START) }.apply { background = null })
        searchInput = EditText(this).apply {
            hint = "Buscar dirección o lugar"
            textSize = 15f; setTextColor(Appearance.text(this@NavigationActivity,Color.parseColor(DriveFlyUi.TEXT)))
            setHintTextColor(Appearance.text(this@NavigationActivity,Color.parseColor("#8E8E93"))); background = null
            maxLines = 1; isSingleLine = true
            setPadding(dp(8), 0, dp(4), 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnEditorActionListener { _, _, _ -> doSearch(); true }
        }
        searchBar.addView(searchInput)
        searchBar.addView(DriveFlyUi.iconButton(this, "search", "Buscar destino") { doSearch() }.apply { background = null })
        root.addView(searchBar)
        quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(-2, dp(38)).apply {
                leftMargin = dp(16); topMargin = dp(76)
            }
        }
        fun favorite(label: String, icon: String, action: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = DriveFlyUi.shape(this@NavigationActivity, "#FFFFFF", 22)
                elevation = dp(1).toFloat(); setPadding(dp(12), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(-2, -1).apply { rightMargin = dp(8) }
                addView(DriveFlyUi.icon(this@NavigationActivity, icon, DriveFlyUi.BLUE, 18))
                addView(DriveFlyUi.text(this@NavigationActivity, label, 12f, true).apply { setPadding(dp(8),0,0,0) })
                setOnClickListener { action() }
                contentDescription = "Abrir destino $label"
            }
        }
        quickRow.addView(favorite("Comida", "pin") { exploreBusinesses("restaurant") })
        quickRow.addView(favorite("Tiendas", "bag") { exploreBusinesses("shopping") })
        quickRow.addView(favorite("Más", "layers") {
            DriveFlySheet.choices(this,"Explorar esta zona", "Explora negocios o abre tus destinos guardados.", listOf(
                "Restaurantes" to { exploreBusinesses("restaurant") },
                "Tiendas" to { exploreBusinesses("shopping") },
                "Cafeterías" to { exploreBusinesses("coffee") },
                "Casa" to { goHome() }, "Trabajo" to { goWork() },
                "Ocultar resultados" to { businessGeneration++; nearbyBusinesses = emptyList(); drawBusinesses(); hidePanels() }))
        })
        root.addView(quickRow)

        // 4. Panel de resultados (lista)
        resultsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#FFFFFF", 28)
            elevation = dp(6).toFloat()
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(132)
            }
        }
        root.addView(resultsPanel)

        // 5. Tarjeta de vista previa
        previewCard = DriveFlySheet(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#FFFFFF", 28)
            elevation = dp(8).toFloat()
            setPadding(dp(24), dp(2), dp(24), dp(20))
            onOffset = { visibleHeight -> moveMapControls(visibleHeight + dp(12)) }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = 0; rightMargin = 0; bottomMargin = 0
            }
        }
        root.addView(previewCard)
        previewCard.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (previewCard.visibility == View.VISIBLE)
                moveMapControls((previewCard.height - previewCard.translationY).toInt() + dp(12))
        }

        // 6. Banner de navegación (giro actual)
        navBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg("#1C1C1E", 24)
            elevation = dp(6).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(84)
            }
        }
        root.addView(navBanner)

        // 7. Barra inferior (ETA, distancia, terminar)
        navBottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg("#1C1C1E", 24)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        root.addView(navBottomBar)

        locationControl = DriveFlyUi.iconButton(this, "locate", "Centrar en mi ubicación") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) startLocation()
            else if (currentLoc != null) { followLocation = true; centerMap(currentLoc!!, 16.5) }
            else {
                Toast.makeText(this, "Activa la ubicación del dispositivo", Toast.LENGTH_LONG).show()
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }.apply {
            background = DriveFlyUi.shape(this@NavigationActivity, "#FFFFFF", 24)
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.BOTTOM or Gravity.END; rightMargin = dp(16); bottomMargin = dp(32)
            }
        }
        root.addView(locationControl)
        createBusinessLegend()
        perspectiveControl = DriveFlyUi.button(this,"2D") {
            threeD = !threeD
            NavigationSession.setCameraMode(threeD)
            (perspectiveControl as TextView).text = if (threeD) "2D" else "3D"
            if (!navigating) mapView.mapboxMap.easeTo(CameraOptions.Builder().pitch(if (threeD) 60.0 else 0.0).build(),MapAnimationOptions.Builder().duration(1100).build())
        }.apply {
            contentDescription = "Cambiar perspectiva 2D o 3D"
            background = DriveFlyUi.shape(this@NavigationActivity,"#FFFFFF",24)
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(16); bottomMargin = dp(88) }
        }
        root.addView(perspectiveControl)
        searchInput.clearFocus()
        root.isFocusableInTouchMode = true; root.requestFocus()
        ReferenceNavigationOverlay.visibilityListener(mapView) { visible -> if(navigating) navBanner.visibility=if(!NavigationSession.state?.message.isNullOrBlank() || (!visible && NavigationSession.nearManeuver())) View.VISIBLE else View.GONE }
        buildHomeCard()
        attachDrawer()
    }

    private fun buildSetupUI() {
        root = FrameLayout(this).apply { setBackgroundColor(Appearance.background(this@NavigationActivity,Color.parseColor(DriveFlyUi.BACKGROUND))) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        }
        center.addView(DriveFlyUi.text(this, "Tu próxima ruta", 32f, true))
        center.addView(DriveFlyUi.text(this, "El mapa será tu pantalla de inicio.", 17f, color = DriveFlyUi.MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        val card = DriveFlyUi.card(this)
        if (recoveringStartup) {
            card.addView(DriveFlyUi.text(this,"Recuperar el mapa",22f,true))
            card.addView(DriveFlyUi.text(this,"El inicio anterior no terminó. Puedes abrir el mapa compatible o consultar el último error.",color=DriveFlyUi.MUTED))
            card.addView(DriveFlyUi.button(this,"Abrir mapa compatible",true) {
                MapExperience.save(this,MapExperience.read(this).copy(enabled=false))
                getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putBoolean("advanced_map",false).putBoolean("map_start_pending",false).commit()
                recreate()
            })
            card.addView(DriveFlyUi.button(this,"Ver último error") {
                DriveFlySheet.choices(this,"Último error",CrashLog.read(this),emptyList())
            })
        } else {
        card.addView(DriveFlyUi.text(this, "Conecta tu mapa", 22f, true))
        card.addView(DriveFlyUi.text(this, "Añade tu token público de Mapbox una sola vez para ver el mapa y buscar destinos.", color = DriveFlyUi.MUTED))
        card.addView(DriveFlyUi.button(this, "Configurar Mapbox", true) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        }
        center.addView(card)
        root.addView(center)
        attachDrawer()
    }

    private fun buildHomeCard() {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;background=SettingsUi.shape(this@NavigationActivity,radius=24)
            elevation=dp(5).toFloat();setPadding(dp(18),dp(16),dp(18),dp(16))
            layoutParams=FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { leftMargin=dp(16);rightMargin=dp(16);bottomMargin=dp(16) }
        }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(DriveFlyUi.icon(this,"bag",DriveFlyUi.BLUE,22),LinearLayout.LayoutParams(dp(22),dp(22)).apply { rightMargin=dp(10) })
        header.addView(SettingsUi.text(this,"Tu turno con Dasher",16f,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(DriveFlyUi.iconButton(this,"gear","Configuración") { startActivity(Intent(this,SettingsActivity::class.java)) }.apply { background=null },LinearLayout.LayoutParams(dp(44),dp(44)))
        card.addView(header)
        homeStatus=SettingsUi.text(this,"",12f,secondary=true).apply { setPadding(0,0,0,dp(12)) };card.addView(homeStatus)
        card.addView(DriveFlyUi.button(this,"Iniciar turno",true) {
            startActivity(Intent(this,MainActivity::class.java).putExtra("start_dasher",true))
        },LinearLayout.LayoutParams(-1,dp(48)))
        homeCard=card;root.addView(card)
        card.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> if(card.visibility==View.VISIBLE) moveMapControls(card.height+dp(26)) }
        updateHomeCard()
    }
    private fun updateHomeCard() {
        val card=homeCard ?: return
        val visible=!navigating && destPlace==null && (!::resultsPanel.isInitialized || resultsPanel.visibility!=View.VISIBLE) && (!::previewCard.isInitialized || previewCard.visibility!=View.VISIBLE)
        card.visibility=if(visible) View.VISIBLE else View.GONE
        val enabled=android.provider.Settings.Secure.getString(contentResolver,android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        homeStatus?.text=if(enabled.contains("$packageName/",true)&&enabled.contains("DashAddressService",true)) "Listo para detectar la dirección de tu pedido" else "Activa el lector de Dasher para detectar tus destinos"
        if(visible) card.post { if(card.visibility==View.VISIBLE) moveMapControls(card.height+dp(26)) }
    }

    private fun attachDrawer() {
        drawer = androidx.drawerlayout.widget.DrawerLayout(this)
        drawer.addView(root, androidx.drawerlayout.widget.DrawerLayout.LayoutParams(-1, -1))
        if (!::searchBar.isInitialized || searchBar.parent != root) {
            root.addView(DriveFlyUi.iconButton(this, "menu", "Abrir menú de DriveFly") { drawer.openDrawer(androidx.core.view.GravityCompat.START) }.apply {
                background = DriveFlyUi.shape(this@NavigationActivity, "#FFFFFF", 24)
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(16); topMargin = dp(12) }
            })
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Appearance.background(this@NavigationActivity,Color.WHITE))
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(24)) }
        brand.addView(DriveFlyUi.icon(this, "arrow", "#FFFFFF", 26).apply {
            background = DriveFlyUi.shape(this@NavigationActivity, DriveFlyUi.BLUE, 16)
            setPadding(dp(12),dp(12),dp(12),dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(44),dp(44))
        })
        brand.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0)
            addView(DriveFlyUi.text(this@NavigationActivity, "DriveFly", 20f, true))
            addView(DriveFlyUi.text(this@NavigationActivity, "Tu compañero de ruta", 12f, color = DriveFlyUi.MUTED))
        })
        menu.addView(brand)
        fun section(label: String) {
            menu.addView(DriveFlyUi.text(this, label, 11f, true, "#8E8E93").apply { letterSpacing = 0.12f; setPadding(dp(12),dp(20),0,dp(8)) })
        }
        fun row(label: String, icon: String, selected: Boolean = false, action: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x10007AFF), DriveFlyUi.shape(this@NavigationActivity, if (selected) "#EDF4FF" else "#FFFFFF", 18), null)
                layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(4) }
                setPadding(dp(12),0,dp(12),0)
                addView(DriveFlyUi.icon(this@NavigationActivity, icon, if (selected) DriveFlyUi.BLUE else "#636366", 21))
                addView(DriveFlyUi.text(this@NavigationActivity, label, 15f, selected, if (selected) DriveFlyUi.BLUE else DriveFlyUi.TEXT).apply {
                    setPadding(dp(14),0,0,0); layoutParams = LinearLayout.LayoutParams(0,-2,1f)
                })
                addView(DriveFlyUi.icon(this@NavigationActivity, "chevron", "#C7C7CC", 14))
                setOnClickListener { drawer.closeDrawer(androidx.core.view.GravityCompat.START); action() }
            }
        }
        section("EXPLORAR")
        menu.addView(row("Mapa y destinos", "map", true) { })
        menu.addView(row("Destinos guardados", "bookmark") {
            if (!::previewCard.isInitialized) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else DriveFlySheet.choices(this,"Destinos guardados",actions = listOf("Casa" to { goHome() }, "Trabajo" to { goWork() }))
        })
        section("EN TU RUTA")
        val floatingRow = row("Mapa flotante", "layers") { if (FloatingMapService.isRunning) stopService(Intent(this,FloatingMapService::class.java)) else openFloating() }
        floatingRow.removeViewAt(floatingRow.childCount - 1)
        floatingToggle = DriveFlySwitch(this).apply {
            contentDescription = "Activar o desactivar mapa flotante"
            isChecked = FloatingMapService.isRunning
            setOnClickListener {
                if (FloatingMapService.isRunning) stopService(Intent(this@NavigationActivity,FloatingMapService::class.java)) else openFloating()
                isChecked = FloatingMapService.isRunning
            }
        }
        floatingRow.addView(floatingToggle)
        menu.addView(floatingRow)
        menu.addView(row("Abrir Dasher", "bag") {
            val launch = packageManager.getLaunchIntentForPackage("com.doordash.driverapp") ?: packageManager.getLaunchIntentForPackage("com.dd.doordash")
            if (launch != null) startActivity(launch) else Toast.makeText(this,"Dasher no está instalado",Toast.LENGTH_LONG).show()
        })
        section("PREFERENCIAS")
        menu.addView(row("Cámara", "gear") { startActivity(Intent(this,CameraSettingsActivity::class.java)) })
        menu.addView(row("Configuración", "gear") { startActivity(Intent(this,SettingsActivity::class.java)) })
        menu.addView(row("Estado y permisos", "shield") { startActivity(Intent(this,SettingsActivity::class.java).putExtra("page","permissions")) })
        menu.addView(row("Diagnóstico", "info") { startActivity(Intent(this,SettingsActivity::class.java).putExtra("page","diagnostic")) })
        menu.addView(row("Ayuda", "question") {
            DriveFlySheet.choices(this,"Tu ruta con DriveFly", "Busca una dirección o toca el mapa para marcar un punto. Desliza la tarjeta para ver más mapa. Arrastra, pellizca y gira con dos dedos; usa 2D/3D para cambiar la perspectiva. El botón de ubicación vuelve a seguirte.", listOf("Entendido" to {}))
        })
        menu.addView(DriveFlyUi.text(this, "DriveFly ${BuildConfig.VERSION_NAME}", 11f, color = "#8E8E93").apply { setPadding(dp(12),dp(28),0,0) })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = DriveFlyUi.shape(this@NavigationActivity, "#FFFFFF", 28)
            clipToOutline = true
            addView(menu)
        }
        val width = minOf(dp(304),resources.displayMetrics.widthPixels - dp(56))
        drawer.addView(scroll,androidx.drawerlayout.widget.DrawerLayout.LayoutParams(width,-1,androidx.core.view.GravityCompat.START))
        drawer.setScrimColor(Appearance.background(this@NavigationActivity,Color.parseColor("#660E1726")))
        drawer.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                floatingToggle?.isChecked = FloatingMapService.isRunning
                DriveFlyUi.chrome(this@NavigationActivity)
            }
        })
        setContentView(drawer)
        DriveFlyUi.chrome(this)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(androidx.core.view.GravityCompat.START) -> drawer.closeDrawer(androidx.core.view.GravityCompat.START)
                    navigating -> DriveFlySheet.choices(this@NavigationActivity,"¿Terminar navegación?",actions = listOf("Terminar" to { endNav() }, "Seguir navegando" to {}))
                    ::previewCard.isInitialized && previewCard.visibility == View.VISIBLE -> hidePanels()
                    ::resultsPanel.isInitialized && resultsPanel.visibility == View.VISIBLE -> hidePanels()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    private fun roundedBg(color: String, r: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(r).toFloat()
            setColor(Appearance.background(this@NavigationActivity,Color.parseColor(color)))
        }

    private fun quickBtn(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(Appearance.text(this@NavigationActivity,Color.parseColor(DriveFlyUi.BLUE)))
            background = roundedBg("#FFFFFF", 20)
            elevation = dp(2).toFloat()
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            }
            setOnClickListener { onClick() }
        }
    }

    // ============ UBICACIÓN ============

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            return
        }
        if (locationListener != null) return
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(l: Location) {
                    if (System.currentTimeMillis() - l.time > 120000) return
                    val previous = currentLoc
                    if (previous != null && System.currentTimeMillis() - previous.time < 20000 &&
                        previous.hasAccuracy() && l.hasAccuracy() && previous.accuracy < 40 && l.accuracy > 80) return
                    val first = currentLoc == null
                    currentLoc = l
                    NavigationSession.onLocation(l)
                    puckProvider.update(l)
                    if (first && destPlace != null && previewCard.visibility == View.VISIBLE) loadPreviewRoute()
                    if (navigating) updateNav(l) else if (followLocation && destPlace == null) centerMap(l, 16.0)
                    if (destPlace == null && !navigating) autoExplore()
                }
            }
            locationListener = listener
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager?.isProviderEnabled(provider) == true)
                    locationManager?.requestLocationUpdates(provider, 1000, 1f, listener)
            }
            val last = listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> try { locationManager?.getLastKnownLocation(provider) } catch (_: Exception) { null } }
                .filter { System.currentTimeMillis() - it.time < 120000 }
                .maxByOrNull { it.time }
            if (last != null) { currentLoc = last; NavigationSession.onLocation(last);puckProvider.update(last); if (destPlace == null) centerMap(last, 16.0) else if (previewCard.visibility == View.VISIBLE && cachedRoute == null) loadPreviewRoute() }
        } catch (e: Exception) {
            locationListener = null
            Toast.makeText(this, "Activa la ubicación para seguir tu recorrido", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 1 && ::mapView.isInitialized) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocation()
                if (pendingFloating) openFloating()
            } else {
                pendingFloating = false
                Toast.makeText(this, "Activa ubicación precisa desde Estado y permisos", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun centerMap(loc: Location, zoom: Double) {
        try {
            if (navigating) { DriveFlyMap.refreshTracking(mapView);return }
            mapView.mapboxMap.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(loc.longitude, loc.latitude))
                    .zoom(zoom+CameraPreferences.current(this).zoom-CameraConfig.BASE_ZOOM+SpeedZoom.offset(CameraPreferences.current(this),SpeedMotion.mph)).bearing(NavigationSession.bearing)
                    .pitch(if (threeD) { if (navigating) CameraPreferences.current(this).tilt.toDouble() else CameraPreferences.current(this).tilt.toDouble() } else 0.0)
                    .build(),MapAnimationOptions.Builder().duration(1100).build()
            )
        } catch (_: Exception) {}
    }

    // ============ BÚSQUEDA ============

    private fun doSearch() {
        val q = searchInput.text.toString().trim()
        searchInput.clearFocus(); root.requestFocus()
        if (q.isEmpty()) { searchInput.error = "Escribe una dirección o un lugar"; return }
        val generation = ++searchGeneration
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
        hidePanels()
        resultsPanel.removeAllViews()
        resultsPanel.addView(TextView(this).apply {
            text = "Buscando…"
            setPadding(dp(16), dp(12), dp(16), dp(12))
        })
        homeCard?.visibility = View.GONE
        resultsPanel.visibility = View.VISIBLE

        Thread {
            try {
                val enc = URLEncoder.encode(q, "UTF-8")
                // Usar ubicación actual para mejores resultados
                val loc = currentLoc
                val viewbox = if (loc != null) {
                    "&viewbox=${loc.longitude - 0.5},${loc.latitude + 0.5},${loc.longitude + 0.5},${loc.latitude - 0.5}"
                } else ""
                val url = "https://nominatim.openstreetmap.org/search?q=$enc&format=json&limit=6$viewbox"
                val conn = URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "DriveFly/1.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val json = conn.getInputStream().bufferedReader().use { it.readText() }

                val places = SearchParser.parse(json).map {
                    Place(it.name, it.address, Point.fromLngLat(it.longitude, it.latitude))
                }

                runOnUiThread {
                    if (isDestroyed || isFinishing || generation != searchGeneration) return@runOnUiThread
                    resultsPanel.removeAllViews()
                    if (places.isEmpty()) {
                        resultsPanel.addView(TextView(this).apply {
                            text = "Sin resultados"
                            setPadding(dp(16), dp(12), dp(16), dp(12))
                        })
                    } else {
                        for (p in places) {
                            val result = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dp(16),dp(12),dp(16),dp(12))
                                minimumHeight = dp(64)
                                addView(DriveFlyUi.icon(this@NavigationActivity,"pin",DriveFlyUi.BLUE,22))
                                addView(LinearLayout(this@NavigationActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = dp(12) }
                                    addView(DriveFlyUi.text(this@NavigationActivity,p.name,15f,true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                                    addView(DriveFlyUi.text(this@NavigationActivity,p.address.removePrefix(p.name).trimStart(',', ' '),12f,color=DriveFlyUi.MUTED).apply {
                                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0,dp(3),0,0)
                                    })
                                })
                                addView(DriveFlyUi.icon(this@NavigationActivity,"chevron","#C7C7CC",14))
                                setOnClickListener { showPreview(p) }
                            }
                            resultsPanel.addView(result)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed || isFinishing || generation != searchGeneration) return@runOnUiThread
                    resultsPanel.removeAllViews()
                    resultsPanel.addView(TextView(this).apply {
                        text = "No se pudo buscar. Comprueba tu conexión e inténtalo de nuevo."
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                    })
                }
            }
        }.start()
    }

    private fun pickMapPlace(point: Point) {
        val generation = ++mapPickGeneration
        val layers = mapView.mapboxMap.getStyle()?.styleLayers?.filter { it.id.contains("poi",true) || it.id.startsWith("nearby-") || it.id == "alt-routes" }?.map { it.id }.orEmpty()
        fun fallback() { showPreview(Place("Punto en el mapa", "%.5f, %.5f".format(Locale.US,point.latitude(),point.longitude()),point)) }
        if (layers.isEmpty()) { pickStandardPlace(point,generation,::fallback); return }
        mapView.mapboxMap.queryRenderedFeatures(RenderedQueryGeometry(mapView.mapboxMap.pixelForCoordinate(point)),RenderedQueryOptions(layers,null)) { result ->
            if (isDestroyed || generation != mapPickGeneration || navigating) return@queryRenderedFeatures
            val feature = result.value?.firstOrNull()?.queriedFeature?.feature
            val alternative = feature?.properties()?.get("route_index")?.takeIf { it.isJsonPrimitive }?.asInt
            if (alternative != null && alternative in routeOptions.indices) { selectRoute(alternative);return@queryRenderedFeatures }
            val name = feature?.properties()?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            val coordinate = feature?.geometry() as? Point
            if (feature != null && !name.isNullOrBlank() && coordinate != null) {
                val id = feature.properties()?.get("mapbox_id")?.takeIf { it.isJsonPrimitive }?.asString
                showPreview(Place(name,feature.properties()?.get("full_address")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),coordinate,id,true))
            } else pickStandardPlace(point,generation,::fallback)
        }
    }

    private fun pickStandardPlace(point: Point,generation: Int,fallback: () -> Unit) {
        if (!DriveFlyMap.advanced(mapView)) { fallback();return }
        mapView.mapboxMap.queryRenderedFeatures(StandardPoi(),RenderedQueryGeometry(mapView.mapboxMap.pixelForCoordinate(point)),null) { result ->
            if (isDestroyed || generation != mapPickGeneration || navigating) return@queryRenderedFeatures
            val feature = result.firstOrNull()
            val name = feature?.name
            if (!name.isNullOrBlank() && feature != null) showPreview(Place(name,"",feature.geometry,business = true)) else fallback()
        }
    }

    private fun createBusinessLegend() {
        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL;background = DriveFlyUi.shape(this@NavigationActivity,"#FFFFFF",16)
            elevation = dp(2).toFloat();setPadding(dp(10),dp(4),dp(8),dp(8));visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(minOf(dp(258),resources.displayMetrics.widthPixels-dp(92)),-2,Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(16);bottomMargin = dp(24) }
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        businessLegendTitle = DriveFlyUi.text(this,"Restaurantes",11f,true).apply { layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
        heading.addView(businessLegendTitle)
        heading.addView(DriveFlyUi.iconButton(this,"search","Buscar negocios en esta zona") { exploreBusinesses(selectedCategory) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(28),dp(28));background = null;setPadding(dp(6),dp(6),dp(6),dp(6))
        })
        legend.addView(heading)
        val colors = LinearLayout(this)
        listOf("Abierto" to "#19A76C","≤30 min" to "#F2B544","Cerrado" to "#8C96A3","Sin dato" to "#FFFFFF").forEach { (label,color) ->
            val chip = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL;layoutParams = LinearLayout.LayoutParams(0,-2,1f)
                addView(View(this@NavigationActivity).apply {
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL;setColor(Appearance.background(this@NavigationActivity,Color.parseColor(color)));if (color == "#FFFFFF") setStroke(dp(1),Appearance.background(this@NavigationActivity,Color.parseColor("#738192"))) }
                    layoutParams = LinearLayout.LayoutParams(dp(7),dp(7)).apply { rightMargin = dp(3) }
                })
                addView(DriveFlyUi.text(this@NavigationActivity,label,9f,color=DriveFlyUi.MUTED))
            };colors.addView(chip)
        }
        legend.addView(colors);businessLegend = legend;root.addView(legend)
    }
    private fun updateLegend(error: String? = null) {
        businessLegend?.visibility = if (!navigating && (nearbyBusinesses.isNotEmpty() || businessLoading || error != null)) View.VISIBLE else View.GONE
        val category = when(selectedCategory) { "shopping" -> "Tiendas";"coffee" -> "Cafeterías";else -> "Restaurantes" }
        businessLegendTitle?.text = error ?: when {
            businessLoading -> "$category · buscando…"
            detailsLoading -> "$category · consultando horarios…"
            nearbyBusinesses.isEmpty() -> "$category · sin resultados"
            else -> "$category · ${nearbyBusinesses.size} lugares"
        }
        updateHomeCard()
        val bottom = if (::previewCard.isInitialized && previewCard.visibility == View.VISIBLE) (previewCard.height-previewCard.translationY).toInt()+dp(12) else if(homeCard?.visibility==View.VISIBLE) (homeCard?.height ?: dp(155))+dp(26) else dp(24)
        moveMapControls(bottom)
    }
    private fun cachedDetails(id: String?): PlacesClient.Details? = id?.let { key ->
        businessCache[key]?.takeIf { System.currentTimeMillis()-it.second < 300000 }?.first
    }
    private fun autoExplore() {
        if (!mapReady || currentLoc == null || businessLoading || navigating || destPlace != null) return
        if (System.currentTimeMillis()-lastBrowseAttempt < 60000) return
        if (lastBrowsePoint != null && nearbyBusinesses.isNotEmpty()) return
        exploreBusinesses("restaurant",silent = true)
    }
    private fun exploreBusinesses(category: String,silent: Boolean = false) {
        if (!silent && !navigating) { searchGeneration++;hidePanels() }
        val generation = ++businessGeneration
        if (selectedCategory != category) { nearbyBusinesses = emptyList();drawBusinesses() }
        selectedCategory = category;businessLoading = true;lastBrowseAttempt = System.currentTimeMillis()
        val center = mapView.mapboxMap.cameraState.center;lastBrowsePoint = center
        updateLegend()
        Thread {
            try {
                val businesses = PlacesClient.nearby(category,center.longitude(),center.latitude(),token)
                runOnUiThread {
                    if (isDestroyed || generation != businessGeneration) return@runOnUiThread
                    businessLoading = false;nearbyBusinesses = businesses
                    val now = System.currentTimeMillis()
                    businesses.forEach { business -> business.id?.let { id -> business.publishedDetails?.let { details ->
                        businessCache[id] = PlacesClient.mergeDetails(cachedDetails(id),details) to now
                    } } }
                    drawBusinesses();updateLegend();refreshBusinessHours()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (isDestroyed || generation != businessGeneration) return@runOnUiThread
                    businessLoading = false;updateLegend("No se pudo buscar · reintentar")
                }
            }
        }.start()
    }
    private fun refreshBusinessHours() {
        if (detailsLoading) return
        if (navigating) return
        val ids = nearbyBusinesses.mapNotNull { it.id }.distinct().filter {
            val cached = cachedDetails(it)
            cached == null || cached.hours != null && cached.timezone == null && cached.hours != "24/7"
        }
        if (ids.isEmpty()) return
        val generation = ++businessDetailsGeneration
        detailsLoading = true;updateLegend()
        Thread {
            val records = mutableMapOf<String,PlacesClient.Details>()
            ids.chunked(100).forEach { batch ->
                try { records.putAll(PlacesClient.detailsBatch(batch,token)) } catch (_: Exception) { }
            }
            // Some account/provider responses omit records from the batch. Resolve a bounded
            // number individually, keeping the same business ID and never guessing a match.
            val missing = ids.filter { id -> val record = records[id];record == null || record.hours == null && !record.permanentlyClosed }
            val retryIds = if (missing.isEmpty()) emptyList() else List(minOf(6,missing.size)) { index -> missing[(hoursFallbackCursor+index)%missing.size] }
            hoursFallbackCursor += retryIds.size
            retryIds.forEach { id ->
                try { records[id] = PlacesClient.details(id,token) } catch (_: Exception) { }
            }
            runOnUiThread {
                if (isDestroyed || generation != businessDetailsGeneration) return@runOnUiThread
                detailsLoading = false
                val now = System.currentTimeMillis();records.forEach { (id,record) -> businessCache[id] = PlacesClient.mergeDetails(cachedDetails(id),record) to now }
                if (businessCache.size > 150) businessCache.entries.removeAll { now-it.value.second >= 300000 }
                drawBusinesses();refreshBusinessPanel();updateLegend()
            }
        }.start()
    }
    private fun drawBusinesses() {
        if (!mapReady) return
        mapView.mapboxMap.getStyle { style ->
            listOf("nearby-labels","nearby-points").forEach { if (style.styleLayerExists(it)) style.removeStyleLayer(it) }
            if (style.styleSourceExists("nearby-source")) style.removeStyleSource("nearby-source")
            if (nearbyBusinesses.isEmpty() || navigating) return@getStyle
            val features = nearbyBusinesses.map { b -> Feature.fromGeometry(Point.fromLngLat(b.lon,b.lat)).apply {
                val details = cachedDetails(b.id)
                val state = BusinessHours.status(details?.hours,details?.timezone,details?.permanentlyClosed ?: false)
                addStringProperty("name",b.name);addStringProperty("full_address",b.address)
                addStringProperty("color",state.state.color);addStringProperty("stroke",if (state.state == BusinessHours.State.UNKNOWN) "#738192" else "#FFFFFF")
                b.id?.let { addStringProperty("mapbox_id",it) }
            } }
            style.addSource(geoJsonSource("nearby-source") { featureCollection(FeatureCollection.fromFeatures(features)) })
            style.addLayer(circleLayer("nearby-points","nearby-source") {
                slot("top");circleRadius(8.0);circleColor(Expression.get("color"));circleStrokeColor(Expression.get("stroke"));circleStrokeWidth(2.5)
            })
            style.addLayer(symbolLayer("nearby-labels","nearby-source") {
                slot("top");textField(Expression.get("name"));textSize(11.5);textColor("#253244")
                textHaloColor("#FFFFFF");textHaloWidth(2.0);textOffset(listOf(0.0,1.3))
            })
        }
    }
    private fun refreshBusinessPanel() { businessPanel?.update(cachedDetails(businessPanelId)) }
    private fun businessDetails(place: Place,panel: BusinessPanel) {
        businessPanel = panel;businessPanelId = place.businessId
        val cached = cachedDetails(place.businessId)
        panel.update(cached,loading = cached == null)
        if (cached != null && (cached.permanentlyClosed || cached.hours == "24/7" || cached.hours != null && cached.timezone != null)) return
        Thread {
            try {
                val id = place.businessId ?: PlacesClient.resolve(place.name,(place.vehicle ?: place.point).longitude(),(place.vehicle ?: place.point).latitude(),token)
                    ?: throw IllegalStateException("No se pudo identificar el negocio")
                val details = PlacesClient.details(id,token)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    businessCache[id] = PlacesClient.mergeDetails(cachedDetails(id),details) to System.currentTimeMillis();drawBusinesses()
                    if (destPlace == place) { businessPanelId = id;panel.update(cachedDetails(id)) }
                }
            } catch (_: Exception) {
                runOnUiThread { if (!isDestroyed && destPlace == place) panel.update(cachedDetails(businessPanelId) ?: cached) }
            }
        }.start()
    }

    // ============ VISTA PREVIA ============

    private fun showPreview(place: Place) {
        val address=place.address.takeIf { Regex("^\\d{1,6}[A-Za-z]?\\s+").containsMatchIn(it) } ?: place.name.takeIf { Regex("^\\d{1,6}[A-Za-z]?\\s+").containsMatchIn(it) }
        if(!place.accessResolved && address!=null && MapExperience.read(this).entrances && currentLoc!=null) {
            val shown=place.copy(accessResolved=true)
            showPreview(shown)
            val request=++searchGeneration;val origin=currentLoc!!
            Thread {
                val access=try { DasherGeocoder.find(address,token,DasherGeocoder.Coordinate(origin.latitude,origin.longitude),true) } catch(_: Exception) { null }
                runOnUiThread {
                    if(isDestroyed||isFinishing||navigating||request!=searchGeneration||destPlace!=shown) return@runOnUiThread
                    val nearby=access?.takeIf { DestinationAccess.distance(DestinationAccess.Point(it.lat,it.lon),DestinationAccess.Point(place.point.latitude(),place.point.longitude()))<=150 }
                    if(nearby!=null) showPreview(place.copy(point=Point.fromLngLat(nearby.lon,nearby.lat),vehicle=Point.fromLngLat(nearby.vehicleLon ?: nearby.lon,nearby.vehicleLat ?: nearby.lat),entrance=nearby.entrance,accessResolved=true))
                }
            }.start()
            return
        }
        if (navigating) endNav()
        hidePanels()
        routeGeneration++
        destPlace = place
        cachedRoute = null
        routeOptions = emptyList();selectedRouteIndex = 0
        routeLoading = false
        routeCoords = emptyList()
        clearRoute()
        followLocation = false
        drawDestination()

        try {
            mapView.mapboxMap.easeTo(
                CameraOptions.Builder().center(place.point).zoom(16.0).pitch(if (threeD) 60.0 else 0.0)
                    .padding(EdgeInsets(dp(130).toDouble(),dp(30).toDouble(),dp(300).toDouble(),dp(30).toDouble())).build(),MapAnimationOptions.Builder().duration(1200).build()
            )
        } catch (_: Exception) {}

        previewCard.removeAllViews()
        previewCard.translationY = 0f
        previewCard.addView(previewCard.handle())
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(DriveFlyUi.text(this, place.name, 20f, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0,-2,1f)
        })
        heading.addView(DriveFlyUi.iconButton(this,"close","Cerrar destino") { hidePanels() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(36),dp(36)).apply { leftMargin = dp(8) }
            setPadding(dp(10),dp(10),dp(10),dp(10))
        })
        previewCard.addView(heading)
        previewCard.addView(DriveFlyUi.text(this,place.address.removePrefix(place.name).trimStart(',', ' ').ifBlank { if (place.business) "Ubicación del negocio" else "Punto seleccionado en el mapa" },13f,color = DriveFlyUi.MUTED).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(),1f); setPadding(0,dp(4),dp(36),dp(12))
        })
        if (place.business) {
            val panel = BusinessPanel(this);previewCard.addView(panel);businessDetails(place,panel)
        }
        previewInfo = DriveFlyUi.text(this,"Buscando una ruta…",13f,true,DriveFlyUi.BLUE).apply { setPadding(0,0,0,dp(14)) }
        previewCard.addView(previewInfo)
        routeChoices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL;visibility = View.GONE }
        previewCard.addView(routeChoices)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(DriveFlyUi.button(this,"Iniciar ruta",true) { fetchRoute() }.apply {
            startRouteButton = this
            layoutParams = LinearLayout.LayoutParams(0,dp(48),1.3f).apply { rightMargin = dp(8) }
        })
        row.addView(DriveFlyUi.button(this,"Flotante") { sendToFloating(place) }.apply {
            layoutParams = LinearLayout.LayoutParams(0,dp(48),1f)
        })
        previewCard.addView(row)
        previewCard.addView(DriveFlyUi.button(this,"Guardar destino") {
            DriveFlySheet.choices(this,"Guardar destino",actions = listOf("Casa" to { saveFavorite("home","Casa") }, "Trabajo" to { saveFavorite("work","Trabajo") }))
        }.apply {
            textSize = 13f; setTextColor(Appearance.text(this@NavigationActivity,Color.parseColor(DriveFlyUi.MUTED))); background = null
            layoutParams = LinearLayout.LayoutParams(-1,dp(40)).apply { topMargin = dp(4) }
        })
        homeCard?.visibility = View.GONE
        previewCard.visibility = View.VISIBLE
        previewCard.post { moveMapControls(previewCard.height + dp(28)) }

        loadPreviewRoute()
    }

    private fun routeSummary(route: RoutingClient.Result): String =
        "${kotlin.math.ceil(route.duration / 60).toInt().coerceAtLeast(1)} min · ${"%.1f".format(route.distance / 1609.34)} mi" + if (route.trafficAware) "" else "\nSin tráfico ni cierres en vivo"

    private fun loadPreviewRoute(startAfter: Boolean = false) {
        val place = destPlace ?: return
        val origin = currentLoc
        if (origin == null) {
            previewInfo?.text = "Esperando tu ubicación. Activa GPS y permite ubicación precisa."
            startRouteButton?.isEnabled = true
            startRouteButton?.alpha = 1f
            return
        }
        if (routeLoading) return
        routeLoading = true
        val generation = ++routeGeneration
        previewInfo?.text = "Calculando ruta…"
        startRouteButton?.isEnabled = false; startRouteButton?.alpha = .5f
        Thread {
            try {
                val route = RoutingClient.fetch(origin.longitude, origin.latitude,(place.vehicle ?: place.point).longitude(),(place.vehicle ?: place.point).latitude(),token)
                runOnUiThread {
                    if (isDestroyed || generation != routeGeneration || destPlace != place) return@runOnUiThread
                    routeLoading = false
                    routeOptions = listOf(route) + route.alternatives;selectedRouteIndex = 0
                    selectRoute(0,fit = false)
                    startRouteButton?.isEnabled = true; startRouteButton?.alpha = 1f
                    (startRouteButton as? TextView)?.text = "Iniciar ruta"
                    drawRoute(); frameRoute()
                    if (startAfter) beginCachedRoute()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed || generation != routeGeneration) return@runOnUiThread
                    routeLoading = false
                    previewInfo?.text = e.message ?: "No se pudo calcular la ruta"
                    startRouteButton?.isEnabled = true; startRouteButton?.alpha = 1f
                    (startRouteButton as? TextView)?.text = "Reintentar ruta"
                }
            }
        }.start()
    }

    private fun frameRoute() {
        if (routeCoords.isEmpty()) return
        val generation = routeGeneration
        mapView.mapboxMap.cameraForCoordinates(routeCoords, CameraOptions.Builder().pitch(if (threeD) 60.0 else 0.0).build(),
            EdgeInsets(dp(150).toDouble(),dp(40).toDouble(),(previewCard.height + dp(40)).toDouble(),dp(72).toDouble()), 17.0, null) { camera ->
            if (!isDestroyed && generation == routeGeneration && !navigating && previewCard.visibility == View.VISIBLE)
                mapView.mapboxMap.easeTo(camera,MapAnimationOptions.Builder().duration(1200).build())
        }
    }

    private fun drawDestination() {
        DestinationMarker.set(mapView,destPlace?.point,kind=if (destPlace?.business==true) DestinationKind.PICKUP else DestinationKind.OTHER)
        if (!mapReady) return
        mapView.mapboxMap.getStyle { style ->
            if (style.styleLayerExists("destination-dot")) style.removeStyleLayer("destination-dot")
            if (style.styleSourceExists("destination-src")) style.removeStyleSource("destination-src")
            val place = destPlace ?: return@getStyle
            style.addSource(geoJsonSource("destination-src") { geometry(place.point) })
            val marker = circleLayer("destination-dot","destination-src") {
                circleOpacity(0.0); circleStrokeOpacity(0.0); circleRadius(10.0); circleColor("#172A4B"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(4.0)
            }
            val puck = style.styleLayers.firstOrNull { it.type == "location-indicator" }?.id
            if (puck != null) style.addLayerBelow(marker,puck) else style.addLayer(marker)
        }
    }

    private fun clearRoute() {
        if (!mapReady) return
        mapView.mapboxMap.getStyle { style ->
            listOf("nav-highlight","nav-route","nav-outline","alt-routes","alt-outline").forEach { if (style.styleLayerExists(it)) style.removeStyleLayer(it) }
            listOf("nav-src","alt-src").forEach { if (style.styleSourceExists(it)) style.removeStyleSource(it) }
        }
    }
    private fun selectRoute(index: Int,fit: Boolean = true) {
        if (index !in routeOptions.indices || navigating) return
        selectedRouteIndex = index;cachedRoute = routeOptions[index]
        val parsed = parseRoute(cachedRoute!!.json);routeCoords = parsed.first;routeSteps = parsed.second
        previewInfo?.text = routeSummary(cachedRoute!!)
        updateRouteChoices();drawRoute()
        if (fit) frameRoute()
    }
    private fun updateRouteChoices() {
        val row = routeChoices ?: return;row.removeAllViews()
        row.visibility = if (routeOptions.size > 1) View.VISIBLE else View.GONE
        routeOptions.forEachIndexed { index,route ->
            row.addView(DriveFlyUi.button(this,(if (index == 0) "Principal" else "Opción ${index+1}")+" · ${kotlin.math.ceil(route.duration/60).toInt().coerceAtLeast(1)} min") { selectRoute(index) }.apply {
                textSize = 11f;setPadding(dp(4),0,dp(4),0)
                background = DriveFlyUi.shape(this@NavigationActivity,if (index == selectedRouteIndex) "#E4F7F5" else "#F1F3F6",12)
                setTextColor(Appearance.text(this@NavigationActivity,Color.parseColor(if (index == selectedRouteIndex) "#087F8B" else "#667384")))
                layoutParams = LinearLayout.LayoutParams(0,dp(36),1f).apply { rightMargin = dp(6);bottomMargin = dp(12) }
                contentDescription = "Elegir ruta ${index+1}: ${routeSummary(route)}"
            })
        }
    }

    private fun sendToFloating(place: Place) {
        // Enviar destino al servicio flotante
        val intent = Intent(this, FloatingMapService::class.java).apply {
            action = FloatingMapService.ACTION_DESTINATION
            putExtra("lat", place.point.latitude())
            putExtra("lon", place.point.longitude())
            putExtra("name", place.name)
            putExtra("vehicleLat",(place.vehicle ?: place.point).latitude());putExtra("vehicleLon",(place.vehicle ?: place.point).longitude());putExtra("entrance",place.entrance)
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa Mostrar sobre otras apps desde Inicio", Toast.LENGTH_LONG).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            startLocation()
            return
        }
        try {
            NavigationSession.start(this,place.point.latitude(),place.point.longitude(),place.name,cachedRoute,routeOptions,currentLoc,vehicleLat=(place.vehicle ?: place.point).latitude(),vehicleLon=(place.vehicle ?: place.point).longitude(),entrance=place.entrance)
            startForegroundService(intent)
            Toast.makeText(this, "Destino enviado al flotante", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            CrashLog.save(this, e)
            Toast.makeText(this, "No se pudo abrir el mapa flotante. Revisa los permisos.", Toast.LENGTH_LONG).show()
        } // Volver a la principal
    }

    // ============ RUTA Y NAVEGACIÓN ============

    private fun fetchRoute() {
        if (cachedRoute != null) beginCachedRoute() else {
            if (currentLoc == null) startLocation()
            loadPreviewRoute(startAfter = true)
        }
    }

    private fun beginCachedRoute() {
        val route = cachedRoute ?: return
        val destination = destPlace ?: return
        NavigationSession.start(this,destination.point.latitude(),destination.point.longitude(),destination.name,route,routeOptions,currentLoc,kind=if (destination.business) DestinationKind.PICKUP else DestinationKind.fromName(destination.name),vehicleLat=(destination.vehicle ?: destination.point).latitude(),vehicleLon=(destination.vehicle ?: destination.point).longitude(),entrance=destination.entrance)
    }

    private fun drawRoute() {
        if (!mapReady || routeCoords.isEmpty()) return
        mapView.mapboxMap.getStyle { style ->
            clearRoute()
            val alternatives = routeOptions.mapIndexedNotNull { index,route ->
                if (index == selectedRouteIndex) null else Feature.fromGeometry(LineString.fromLngLats(route.route.coordinates.map { Point.fromLngLat(it.longitude,it.latitude) })).apply { addNumberProperty("route_index",index) }
            }
            if (alternatives.isNotEmpty()) {
                style.addSource(geoJsonSource("alt-src") { featureCollection(FeatureCollection.fromFeatures(alternatives)) })
                style.addLayer(lineLayer("alt-outline","alt-src") { slot("middle");lineColor("#FFFFFF");lineOpacity(if (Appearance.dark(this@NavigationActivity)) 0.0 else 1.0);lineWidth(Expression.fromRaw(NavigationZoomScale.expression(11.0)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })
                style.addLayer(lineLayer("alt-routes","alt-src") { slot("middle");lineColor(if(Appearance.dark(this@NavigationActivity)) "#71B4DA" else "#AEA2F5");lineWidth(Expression.fromRaw(NavigationZoomScale.expression(7.0)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })
            }
            val remaining = if (navigating) NavigationSession.remainingCoordinates().map { Point.fromLngLat(it.longitude,it.latitude) } else routeCoords
            val activeFeatures = if (remaining.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(remaining))) else emptyList()
            style.addSource(geoJsonSource("nav-src") { featureCollection(FeatureCollection.fromFeatures(activeFeatures)) })
            style.addLayer(lineLayer("nav-outline","nav-src") { slot("middle");lineColor(RouteColors.casing(this@NavigationActivity));lineEmissiveStrength(1.0);lineOpacity(1.0);lineWidth(Expression.fromRaw(NavigationZoomScale.expression(20.0)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })
            style.addLayer(lineLayer("nav-route","nav-src") { slot("middle");lineColor(RouteColors.line(this@NavigationActivity));lineEmissiveStrength(1.0);lineWidth(Expression.fromRaw(NavigationZoomScale.expression(15.0)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })
            style.addLayer(lineLayer("nav-highlight","nav-src") { slot("middle");lineColor(RouteColors.line(this@NavigationActivity));lineEmissiveStrength(1.0);lineWidth(Expression.fromRaw(NavigationZoomScale.expression(1.2)));lineOffset(Expression.fromRaw(NavigationZoomScale.expression(-5.5)));lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND) })
            DriveFlyMap.raisePuck(mapView,"nav-route")
            drawDestination()
        }
    }

    private fun startNav() {
        navigating = true
        homeCard?.visibility = View.GONE
        businessLegend?.visibility = View.GONE
        drawBusinesses()
        drawRoute()
        searchBar.visibility = View.GONE
        quickRow.visibility = View.GONE
        navBanner.visibility = if(!NavigationSession.state?.message.isNullOrBlank() || (!ReferenceNavigationOverlay.hasBubble(mapView) && NavigationSession.nearManeuver())) View.VISIBLE else View.GONE
        navBottomBar.visibility = View.VISIBLE
        updateNavUI()
        navBottomBar.post { if (navigating) moveMapControls(navBottomBar.height+dp(16)) }
    }

    private fun updateNavUI() {
        if (stepIdx >= routeSteps.size) return
        val step = routeSteps[stepIdx]
        navBanner.visibility=if(!NavigationSession.state?.message.isNullOrBlank() || (!ReferenceNavigationOverlay.hasBubble(mapView) && NavigationSession.nearManeuver())) View.VISIBLE else View.GONE

        navBanner.removeAllViews()
        navBanner.addView(TextView(this).apply {
            text = when {
                step.instruction.contains("izquierda") -> "↰"
                step.instruction.contains("derecha") -> "↱"
                step.instruction.contains("destino") -> "◎"
                else -> "↑"
            }
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 0, dp(12), 0)
        })
        val txtCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        txtCol.addView(TextView(this).apply {
            text = NavigationSession.state?.message?.takeIf { it.isNotBlank() } ?: step.instruction
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        if (step.road.isNotEmpty()) {
            txtCol.addView(TextView(this).apply {
                text = step.road
                textSize = 14f
                setTextColor(Appearance.text(this@NavigationActivity,Color.parseColor("#B0D0FF")))
            })
        }
        navBanner.addView(txtCol)

        // Barra inferior
        navBottomBar.removeAllViews()
        val totalM = NavigationSession.progress?.remaining ?: routeSteps.drop(stepIdx).sumOf { it.distM }
        val mins = kotlin.math.ceil((NavigationSession.progress?.seconds ?: cachedRoute?.duration ?: 0.0)/60).toInt()
        val seconds=NavigationSession.progress?.seconds ?: cachedRoute?.duration ?: 0.0
        val eta=java.text.SimpleDateFormat("h:mm",java.util.Locale.US).format(java.util.Date(System.currentTimeMillis()+(seconds*1000).toLong()))
        navBottomBar.background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(if(Appearance.dark(this)) "#EA344256" else "#D98B8D89"),Color.parseColor(if(Appearance.dark(this)) "#EC24364C" else "#DD676C77"))).apply { cornerRadius=dp(26).toFloat();setStroke(dp(1),Color.parseColor("#408D97AD")) }
        navBottomBar.addView(TextView(this).apply {
            val arrival="$eta ETA";val duration="$mins min";val distance="${"%.1f".format(totalM/1609.34)} mi"
            val all="$arrival   $duration   $distance"
            text=android.text.SpannableString(all).apply { setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#56DB5C")),arrival.length+3,arrival.length+3+duration.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            textSize=17f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.WHITE);gravity=Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(0,-2,1f)
            contentDescription=all+if(cachedRoute?.trafficAware==true) "" else ". Sin tráfico en vivo"
        })
        navSpeedText=null
        navBottomBar.addView(DriveFlyUi.iconButton(this,"close","Terminar ruta") { endNav() }.apply { background=null },LinearLayout.LayoutParams(dp(44),dp(44)))

    }

    private fun updateRemainingRoute(point: Point) {
        val coordinates = NavigationSession.remainingCoordinates(point.longitude(),point.latitude())
        val features = if (coordinates.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates.map { Point.fromLngLat(it.longitude,it.latitude) }))) else emptyList()
        mapView.mapboxMap.getStyle { style ->
            (style.getSource("nav-src") as? com.mapbox.maps.extension.style.sources.generated.GeoJsonSource)?.featureCollection(FeatureCollection.fromFeatures(features))
        }
    }

    private fun updateNav(loc: Location) {
        if (!navigating || routeSteps.isEmpty() || stepIdx >= routeSteps.size) return

        val step = routeSteps[stepIdx]
        val r = FloatArray(1)
        Location.distanceBetween(
            loc.latitude, loc.longitude,
            step.point.latitude(), step.point.longitude(), r
        )

        NavigationSession.onLocation(loc)
        NavigationSession.progress?.let { progress ->
            val index = progress.step.coerceIn(0,routeSteps.lastIndex)
            if (index != stepIdx) stepIdx = index
        }
        updateNavUI()

    }

    private fun fetchRouteSilent() { NavigationSession.request() }

    private fun endNav(arrived: Boolean = false) {
        if (NavigationSession.state != null) { NavigationSession.stop(arrived);return }
        sharedRouteJson = null
        routeGeneration++
        navigating = false
        navBanner.visibility = View.GONE
        navBottomBar.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        quickRow.visibility = View.VISIBLE
        routeSteps = emptyList()
        routeCoords = emptyList()
        stepIdx = 0
        routeOptions = emptyList();cachedRoute = null;selectedRouteIndex = 0
        destPlace = null;followLocation = false
        searchGeneration++;mapPickGeneration++;businessDetailsGeneration++
        hidePanels(clearSelection=false)
        drawDestination();clearRoute()
        DriveFlyMap.refreshTracking(mapView)
        drawBusinesses();updateLegend();updateHomeCard()
    }

    // ============ ACCIONES RÁPIDAS ============

    private fun goHome() = openFavorite("home", "Casa")
    private fun goWork() = openFavorite("work", "Trabajo")

    private fun openFavorite(key: String, label: String) {
        val prefs = getSharedPreferences("mapa_flotante", MODE_PRIVATE)
        if (!prefs.contains("${key}_lat")) {
            Toast.makeText(this, "Busca un destino y pulsa Guardar $label en su tarjeta", Toast.LENGTH_LONG).show()
            return
        }
        showPreview(Place(label, prefs.getString("${key}_address", "").orEmpty(),
            Point.fromLngLat(prefs.getFloat("${key}_lon", 0f).toDouble(), prefs.getFloat("${key}_lat", 0f).toDouble())))
    }
    private fun saveFavorite(key: String, label: String) {
        val place = destPlace ?: run {
            Toast.makeText(this, "Selecciona primero un resultado de búsqueda", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("mapa_flotante", MODE_PRIVATE).edit()
            .putFloat("${key}_lat", place.point.latitude().toFloat())
            .putFloat("${key}_lon", place.point.longitude().toFloat())
            .putString("${key}_address", place.address).apply()
        Toast.makeText(this, "$label guardado", Toast.LENGTH_SHORT).show()
    }

    private fun parseRoute(json: String): Pair<List<Point>, List<RouteStep>> {
        val parsed = RouteParser.parse(json)
        return parsed.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) } to
            parsed.steps.map { RouteStep(it.instruction, it.road, it.distance,
                Point.fromLngLat(it.location.longitude, it.location.latitude)) }
    }

    private fun openFloating() {
        if (token.isEmpty()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingFloating = true
            startLocation(); return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            pendingFloating = true
            Toast.makeText(this, "Permite mostrar el mapa sobre otras apps", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")))
            return
        }
        pendingFloating = false
        try {
            startForegroundService(Intent(this, FloatingMapService::class.java))
            Toast.makeText(this, "Mapa flotante activado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            CrashLog.save(this, e)
            Toast.makeText(this, "No se pudo iniciar el mapa. Revisa los permisos.", Toast.LENGTH_LONG).show()
        }
    }

    private fun moveMapControls(bottom: Int) {
        locationControl?.let { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = bottom; view.layoutParams = it }
        }
        perspectiveControl?.let { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = bottom + dp(56); view.layoutParams = it }
        }
        businessLegend?.let { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = bottom;view.layoutParams = it }
        }
        if (::mapView.isInitialized) {
            val legalBottom = bottom + if (businessLegend?.visibility == View.VISIBLE) (businessLegend?.height ?: dp(48)) + dp(6) else 0
            mapView.logo.updateSettings { marginBottom = legalBottom.toFloat() }
            mapView.attribution.updateSettings { marginBottom = legalBottom.toFloat() }
        }
    }

    private fun hidePanels(clearSelection: Boolean = true) {
        if (clearSelection) {
            routeGeneration++; routeLoading = false; cachedRoute = null
            businessPanel = null;businessPanelId = null
            routeOptions = emptyList();selectedRouteIndex = 0
            destPlace = null; routeCoords = emptyList(); clearRoute(); drawDestination()
            if (::mapView.isInitialized) mapView.mapboxMap.setCamera(CameraOptions.Builder().padding(EdgeInsets(0.0,0.0,0.0,0.0)).build())
        }
        moveMapControls(dp(24))
        resultsPanel.visibility = View.GONE
        previewCard.visibility = View.GONE
        updateHomeCard()
    }
}
