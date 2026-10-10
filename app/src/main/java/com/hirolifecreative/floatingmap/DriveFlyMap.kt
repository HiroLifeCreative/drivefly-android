package com.hirolifecreative.floatingmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.extension.style.expressions.generated.Expression

/** Feed the map puck from the same Android GPS fixes used for route origins. */
class DriveFlyLocationProvider : LocationProvider {
    private val consumers = mutableSetOf<LocationConsumer>()
    private var last: Location? = null
    private var map: MapView? = null
    fun attach(mapView: MapView) { map = mapView }
    override fun registerLocationConsumer(locationConsumer: LocationConsumer) { consumers += locationConsumer; last?.let { send(locationConsumer,it) } }
    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) { consumers -= locationConsumer }
    fun update(location: Location) {
        last = location
        RoadSigns.update(location)
        map?.let { NavigationMapTracking.update(it,NavigationSession.visualFix ?: NavigationSession.latest ?: location) }
        consumers.toList().forEach { send(it,location) }
    }
    private fun send(consumer: LocationConsumer, fix: Location) {
        consumer.onLocationUpdated(Point.fromLngLat(fix.longitude,fix.latitude))
        if (fix.hasBearing() && fix.hasSpeed() && fix.speed >= 2.5) consumer.onBearingUpdated(NavigationSession.bearing)
        consumer.onHorizontalAccuracyRadiusUpdated(fix.accuracy.toDouble())
    }
}

object DriveFlyMap {
    private val puckHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingPucks=java.util.WeakHashMap<MapView,Runnable>()
    private val pendingPuckKeys=java.util.WeakHashMap<MapView,PuckKey>()
    private val puckProviders = java.util.WeakHashMap<MapView,DriveFlyLocationProvider>()
    private data class PuckKey(val native: Boolean,val dark: Boolean,val tilt: Int,val size: Int)
    private val puckConfigs = java.util.WeakHashMap<MapView,PuckKey>()
    private val indicators = java.util.WeakHashMap<MapView,Pair<Point?,Double?>>()
    fun renderedPoint(map: MapView): Point? = indicators[map]?.first
    fun renderedBearing(map: MapView): Double? = indicators[map]?.second
    fun advanced(map: MapView) = MapExperience.read(map.context).enabled

    fun applyStandard(map: MapView,style: Style) {
        if(!advanced(map)) return
        val config=MapExperience.read(map.context)
        fun property(name: String,value: Any) {
            val v=when(value) { is Boolean -> com.mapbox.bindgen.Value.valueOf(value);is Double -> com.mapbox.bindgen.Value.valueOf(value);else -> com.mapbox.bindgen.Value.valueOf(value.toString()) }
            style.setStyleImportConfigProperty("basemap",name,v)
        }
        property("theme","default")
        property("lightPreset",config.lightPreset(Appearance.dark(map.context),java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)))
        property("show3dObjects",true);property("show3dBuildings",config.buildings);property("show3dTrees",config.trees)
        property("show3dLandmarks",config.landmarks);property("show3dFacades",config.facades);property("showHdRoads",config.detailedRoads)
        listOf("showRoadLabels","showPlaceLabels","showPointOfInterestLabels","showPedestrianRoads","showTransitLabels").forEach { property(it,true) }
        property("densityPointOfInterestLabels",4.0)
    }
    fun load(map: MapView, ready: (Style) -> Unit) {
        val advanced = advanced(map)
        val dark = Appearance.dark(map.context)
        map.mapboxMap.loadStyle(if (advanced) "mapbox://styles/mapbox/standard" else if (dark) Style.DARK else Style.LIGHT) { style ->
            if (advanced) {
                applyStandard(map,style)
                MapLighting.observe(map)
            } else if (style.styleSourceExists("composite")) {
                style.addLayer(fillExtrusionLayer("drivefly-buildings","composite") {
                    sourceLayer("building");minZoom(15.0)
                    fillExtrusionColor(if (dark) "#2E4664" else "#E8EEE5");fillExtrusionOpacity(.95)
                    fillExtrusionHeight(Expression.get("height"));fillExtrusionBase(Expression.get("min_height"))
                })
            }
            if (!advanced && dark) {
                // Recolor existing geography; retain the source data and label hierarchy.
                style.styleLayers.forEach { layer ->
                    val propertyAndColor = when {
                        layer.type == "background" -> "background-color" to "#203952"
                        layer.type == "fill" && layer.id == "water" -> "fill-color" to "#13475F"
                        layer.type == "fill" && layer.id.contains("park") -> "fill-color" to "#172F40"
                        layer.type == "line" && layer.id.startsWith("road-") && !layer.id.contains("case") -> "line-color" to "#7C95B8"
                        else -> null
                    }
                    propertyAndColor?.let { (property,color) -> style.setStyleLayerProperty(layer.id,property,com.mapbox.bindgen.Value.valueOf(color)) }
                }
            }
            // Real address numbers from Mapbox Streets, shown only at readable zoom.
            style.addSource(com.mapbox.maps.extension.style.sources.generated.vectorSource("drivefly-addresses") { url("mapbox://mapbox.mapbox-streets-v8") })
            style.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer("drivefly-house-numbers","drivefly-addresses") {
                sourceLayer("housenum_label");minZoom(18.0);slot("top")
                textField(Expression.toString(Expression.get("house_num")))
                textSize(11.0);textColor(if (dark) "#CFD7E3" else "#596573");textHaloColor(if (dark) "#151A22" else "#FFFFFF");textHaloWidth(1.5)
            })
            // Live closures from the Traffic tileset, shared by both map styles.
            style.addSource(com.mapbox.maps.extension.style.sources.generated.vectorSource("drivefly-traffic") { url("mapbox://mapbox.mapbox-traffic-v1") })
            style.addLayer(com.mapbox.maps.extension.style.layers.generated.lineLayer("drivefly-road-closures","drivefly-traffic") {
                sourceLayer("traffic");slot("top");minZoom(10.0)
                filter(Expression.eq(Expression.get("closed"),Expression.literal("yes")))
                lineColor("#FF5368");lineWidth(7.0);lineDasharray(listOf(1.0,1.0))
            })
            style.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer("drivefly-closure-labels","drivefly-traffic") {
                sourceLayer("traffic");slot("top");minZoom(14.0)
                filter(Expression.eq(Expression.get("closed"),Expression.literal("yes")))
                symbolPlacement(com.mapbox.maps.extension.style.layers.properties.generated.SymbolPlacement.LINE)
                symbolSpacing(350.0);textField("CALLE CERRADA");textSize(11.0)
                textColor("#FF5368");textHaloColor(if (dark) "#101318" else "#FFFFFF");textHaloWidth(2.0)
            })
            RoadSigns.attach(map,style)
            MapWeather.attach(map,style)
            ready(style)
        }
    }
    fun followNavigationPuck(map: MapView,following: () -> Boolean,onPosition: (Point) -> Unit) {
        NavigationMapTracking.attach(map,following,onPosition)
    }
    fun refreshTracking(map: MapView) {
        val c=CameraPreferences.current(map.context)
        val key=PuckKey(c.nativePuck3D,Appearance.dark(map.context),c.puckTilt,c.puckSizePercent)
        if (puckConfigs[map]!=key && pendingPuckKeys[map]!=key) puckProviders[map]?.let { provider ->
            pendingPucks.remove(map)?.let { puckHandler.removeCallbacks(it) }
            val task=Runnable { pendingPucks.remove(map);pendingPuckKeys.remove(map);if(map.isAttachedToWindow) puck(map,provider) }
            pendingPuckKeys[map]=key;pendingPucks[map]=task;puckHandler.postDelayed(task,350)
        }
        val fix = NavigationSession.visualFix ?: NavigationSession.latest ?: return
        NavigationMapTracking.update(map,fix)
    }
    fun setRendered(map: MapView,point: Point,bearing: Double) { indicators[map] = point to bearing }
    fun raisePuck(map: MapView,routeLayer: String) {
        map.mapboxMap.getStyle { style ->
            // Slots only order layers in Standard. Classic Light/Dark needs explicit
            // ordering each time route layers are recreated.
            val details = style.styleLayers.filter {
                it.id == "drivefly-road-closures" ||
                    (it.type in listOf("symbol","circle") && !it.id.contains("location",true))
            }.map { it.id }
            details.forEach { id -> style.moveStyleLayer(id,com.mapbox.maps.LayerPosition(null,null,null)) }
            val above = details.lastOrNull() ?: routeLayer
            map.location.updateSettings { layerAbove = null;layerBelow = null;slot = "top" }
            map.location.updateSettings { slot = "top";layerAbove = above;layerBelow = null }
        }
    }
    fun puck(map: MapView, provider: DriveFlyLocationProvider) {
        PuckRecovery.initialize(map.context)
        val config=CameraPreferences.current(map.context)
        pendingPucks.remove(map)?.let { puckHandler.removeCallbacks(it) };pendingPuckKeys.remove(map)
        val newProvider=puckProviders[map] !== provider
        puckProviders[map]=provider
        puckConfigs[map]=PuckKey(config.nativePuck3D,Appearance.dark(map.context),config.puckTilt,config.puckSizePercent)
        provider.attach(map)
        if(newProvider) map.location.setLocationProvider(provider)
        PuckRecovery.using(map,config.nativePuck3D)
        val showLocation=if(newProvider) NavigationSession.state==null else map.location.enabled
        try { map.location.updateSettings {
            slot = "top"
            enabled = showLocation; pulsingEnabled = false; puckBearingEnabled = true
            puckBearing = com.mapbox.maps.plugin.PuckBearing.COURSE
            locationPuck = if (config.nativePuck3D) com.mapbox.maps.plugin.LocationPuck3D(
                modelUri = "asset://navigation-arrow.glb",
                modelScale = List(3) { 24f*config.puckSizePercent/100f },
                modelScaleExpression = NavigationZoomScale.modelExpression(24.0*config.puckSizePercent/100.0),
                modelScaleMode = com.mapbox.maps.plugin.ModelScaleMode.VIEWPORT,modelRotation = listOf(PuckOrientation.rotationX(config.puckTilt),0f,0f),
                modelCastShadows = false,modelReceiveShadows = false,modelEmissiveStrength = 1f) else {
                val arrow = Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
                NavigationPuckArtwork.draw(Canvas(arrow),96f,96f,Appearance.dark(map.context),PuckOrientation.rotationX(config.puckTilt))
                val transparent = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                LocationPuck2D(topImage=ImageHolder.from(transparent),bearingImage=ImageHolder.from(arrow),shadowImage=ImageHolder.from(transparent),scaleExpression=NavigationZoomScale.expression(1.35*config.puckSizePercent/100.0))
            }
        } } catch(error: Exception) {
            CrashLog.save(map.context,error)
            PuckRecovery.fallback(map.context)
            if(config.nativePuck3D) puck(map,provider)
        }
    }
}
