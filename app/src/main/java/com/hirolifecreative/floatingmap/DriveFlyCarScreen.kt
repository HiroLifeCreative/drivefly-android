package com.hirolifecreative.floatingmap

import android.Manifest
import android.app.Presentation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs

/** The host owns the controls; Mapbox renders to its surface through a private display. */
class DriveFlyCarScreen(ctx: CarContext) : Screen(ctx), SurfaceCallback, LocationListener {
    private val handler=Handler(Looper.getMainLooper())
    private val appManager=ctx.getCarService(AppManager::class.java)
    private val navManager=ctx.getCarService(NavigationManager::class.java)
    private val locationManager=ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var surface: Surface?=null
    private var display: VirtualDisplay?=null
    private var presentation: Presentation?=null
    private var map: MapView?=null
    private var provider: DriveFlyLocationProvider?=null
    private var navigating=false
    private var following=true
    private var zoomOffset=0.0
    private var visible=Rect()
    private var disposed=false
    private var lastTemplateAt=0L
    private var status="Esperando el destino de Dasher"
    private val observer: (NavigationSession.State?)->Unit = { update(it) }

    init {
        navManager.setNavigationManagerCallback(object: NavigationManagerCallback {
            override fun onStopNavigation() { NavigationSession.stop() }
        })
        appManager.setSurfaceCallback(this)
        lifecycle.addObserver(object: DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { startLocations() }
            override fun onStop(owner: LifecycleOwner) { locationManager.removeUpdates(this@DriveFlyCarScreen) }
            override fun onDestroy(owner: LifecycleOwner) {
                disposed=true;handler.removeCallbacksAndMessages(null)
                NavigationSession.remove(observer);locationManager.removeUpdates(this@DriveFlyCarScreen)
                if(navigating) navManager.navigationEnded()
                appManager.setSurfaceCallback(null);releaseSurface()
            }
        })
        NavigationSession.observe(ctx,observer)
    }
    private fun startLocations() {
        if(ContextCompat.checkSelfPermission(carContext,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            status="Activa la ubicación de DriveFly en el teléfono";invalidate();return
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this)
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { NavigationSession.onLocation(it) }
        } catch(e: Exception) { CrashLog.save(carContext,e);status="Revisa la ubicación en el teléfono";invalidate() }
    }
    override fun onLocationChanged(location: Location) {
        NavigationSession.onLocation(location)
        // No-route GPS fixes intentionally do not emit session changes; the car still follows.
        if(NavigationSession.state?.route==null) update(NavigationSession.state)
    }
    private fun update(state: NavigationSession.State?) {
        if(disposed) return
        val active=state?.route!=null
        if(active!=navigating) {
            navigating=active
            if(active) navManager.navigationStarted() else navManager.navigationEnded()
        }
        status=state?.message?.takeIf { it.isNotBlank() } ?: state?.name ?: "Esperando el destino de Dasher"
        val fix=NavigationSession.visualFix ?: NavigationSession.latest
        if(fix!=null) provider?.update(fix)
        map?.let { mv -> DestinationMarker.set(mv,state?.let { Point.fromLngLat(it.lon,it.lat) },kind=state?.kind ?: DestinationKind.OTHER) }
        map?.mapboxMap?.getStyle { style ->
            val coordinates=NavigationSession.remainingCoordinates().map { Point.fromLngLat(it.longitude,it.latitude) }
            style.getSourceAs<GeoJsonSource>("car-route")?.geometry(LineString.fromLngLats(coordinates))
        }
        if(following && fix!=null) follow(fix)
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastTemplateAt>=1000 || !active) { lastTemplateAt=now;invalidate() }
    }
    private fun follow(fix: Location) {
        val active=NavigationSession.state!=null
        val center=Point.fromLngLat(fix.longitude,fix.latitude)
        val w=map?.width ?: 0;val h=map?.height ?: 0
        val area=visible.takeIf { !it.isEmpty } ?: Rect(0,0,w,h)
        // Keep the position inside the unobscured host map, beneath the route ahead.
        val pad=EdgeInsets(area.top.toDouble()+area.height()*.25,area.left.toDouble(),
            (h-area.bottom).coerceAtLeast(0).toDouble(),(w-area.right).coerceAtLeast(0).toDouble())
        val config=CameraPreferences.current(carContext)
        val zoom=SpeedZoom.target(config,SpeedMotion.mph)+(if(active) 0.0 else -.645)+zoomOffset
        map?.mapboxMap?.easeTo(CameraOptions.Builder().center(center).zoom(zoom)
            .pitch(if(NavigationSession.threeD) config.tilt.toDouble() else 0.0)
            .bearing(if(active) NavigationSession.bearing else 0.0).padding(pad).build(),MapAnimationOptions.Builder().duration(900).build())
    }
    override fun onGetTemplate(): Template {
        val actions=ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Centrar").setOnClickListener { following=true;NavigationSession.latest?.let(::follow) }.build())
            .addAction(Action.Builder().setTitle("+").setOnClickListener { zoomOffset=(zoomOffset+.5).coerceAtMost(3.0);NavigationSession.latest?.let(::follow) }.build())
            .addAction(Action.Builder().setTitle("−").setOnClickListener { zoomOffset=(zoomOffset-.5).coerceAtLeast(-4.0);NavigationSession.latest?.let(::follow) }.build())
        if(NavigationSession.state!=null) actions.addAction(Action.Builder().setTitle("Terminar").setOnClickListener { NavigationSession.stop() }.build())
        val builder=NavigationTemplate.Builder().setActionStrip(actions.build())
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { enabled -> following=!enabled; if(!enabled) NavigationSession.latest?.let(::follow) }
        val progress=NavigationSession.progress
        if(progress!=null && NavigationSession.state?.route!=null) {
            val seconds=progress.seconds.coerceAtLeast(0.0).toLong()
            builder.setDestinationTravelEstimate(TravelEstimate.Builder(
                Distance.create(progress.remaining.coerceAtLeast(0.0)/1609.344,Distance.UNIT_MILES),
                java.time.ZonedDateTime.now().plusSeconds(seconds)).setRemainingTimeSeconds(seconds).build())
        }
        val state=NavigationSession.state
        val route=state?.route?.route
        val index=NavigationSession.progress?.step ?: if((route?.steps?.size ?: 0)>1) 1 else 0
        val current=route?.steps?.getOrNull(index)
        val cue=current?.instruction ?: status
        val meters=if(current==null) 0.0 else CarNavigationProgress.metersToStep(route!!.steps,index,NavigationSession.progress?.remaining)
        builder.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(Step.Builder(cue).build(),Distance.create(meters,Distance.UNIT_METERS)).build())
        return builder.build()
    }
    override fun onSurfaceAvailable(container: SurfaceContainer) {
        releaseSurface()
        val target=container.surface ?: return
        surface=target
        if(container.width<=0 || container.height<=0 || !target.isValid) return
        val token=carContext.getSharedPreferences("mapa_flotante",Context.MODE_PRIVATE).getString("mapbox_token","").orEmpty()
        if(token.isBlank()) { status="Configura Mapbox en DriveFly desde el teléfono";invalidate();return }
        try {
            MapboxOptions.accessToken=token
            val dm=carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val vd=dm.createVirtualDisplay("DriveFly Android Auto",container.width,container.height,container.dpi.coerceAtLeast(160),target,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY) ?: error("Sin pantalla virtual")
            display=vd
            val p=Presentation(carContext,vd.display);presentation=p
            val mv=MapView(p.context,MapInitOptions(p.context,textureView=true));map=mv
            mv.setViewTreeLifecycleOwner(this)
            p.setContentView(mv);p.show()
            val lp=DriveFlyLocationProvider();provider=lp
            DriveFlyMap.load(mv) { style ->
                if(map!==mv || disposed) return@load
                DriveFlyMap.puck(mv,lp)
                style.addSource(geoJsonSource("car-route") { geometry(LineString.fromLngLats(emptyList())) })
                style.addLayer(lineLayer("car-route-line","car-route") {
                    slot("middle");lineColor(RouteColors.line(carContext));lineEmissiveStrength(1.0)
                    lineWidth(9.0);lineCap(LineCap.ROUND);lineJoin(LineJoin.ROUND)
                })
                update(NavigationSession.state)
            }
        } catch(e: Exception) { CrashLog.save(carContext,e);status="No se pudo iniciar el mapa. Revisa diagnóstico en el teléfono";releaseSurface();invalidate() }
    }
    override fun onVisibleAreaChanged(area: Rect) { visible=Rect(area);NavigationSession.latest?.let(::follow) }
    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        val received=container.surface;val owned=surface
        releaseSurface()
        if(received!==owned) received?.release()
    }
    override fun onScroll(distanceX: Float,distanceY: Float) {
        following=false
        map?.let { mv ->
            val c=mv.mapboxMap.coordinateForPixel(ScreenCoordinate(mv.width/2.0+distanceX,mv.height/2.0+distanceY))
            mv.mapboxMap.setCamera(CameraOptions.Builder().center(c).build())
        }
    }
    override fun onScale(focusX: Float,focusY: Float,scaleFactor: Float) {
        if(scaleFactor<=0 || !scaleFactor.isFinite()) return
        zoomOffset=(zoomOffset+kotlin.math.ln(scaleFactor.toDouble())/kotlin.math.ln(2.0)).coerceIn(-4.0,3.0)
        NavigationSession.latest?.let(::follow)
    }
    private fun releaseSurface() {
        map?.onDestroy();map=null;provider=null
        presentation?.dismiss();presentation=null
        display?.release();display=null
        surface?.release();surface=null
    }
}
