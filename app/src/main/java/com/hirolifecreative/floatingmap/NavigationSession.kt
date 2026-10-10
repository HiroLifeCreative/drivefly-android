package com.hirolifecreative.floatingmap

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONArray

/** Both map components live in :map and consume this single route and reroute request. */
object NavigationSession {
    data class State(val lat: Double,val lon: Double,val name: String,val route: RoutingClient.Result?,val options: List<RoutingClient.Result>,val message: String = "",val loading: Boolean = false,val kind: DestinationKind = DestinationKind.OTHER,val vehicleLat: Double=lat,val vehicleLon: Double=lon,val entrance: Boolean=false,val merchant: String="",val unit: String="",val arrivalSpoken: Boolean=false)
    data class Progress(val step: Int,val remaining: Double,val seconds: Double)
    var state: State? = null;private set
    var progress: Progress? = null;private set
    var latest: Location? = null;private set
    var visualFix: Location? = null;private set
    var bearing = 0.0;private set
    var threeD = true;private set
    fun setCameraMode(enabled: Boolean) { threeD = enabled;emit() }
    var arrived = false;private set
    private var context: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<(State?) -> Unit>()
    private val voiceCues = VoiceCueTracker()
    private val monitor = RouteMonitor()
    private val visualTracker = RouteVisualTracker()
    fun remainingCoordinates(lon: Double? = null,lat: Double? = null): List<RouteParser.Coordinate> {
        val points = state?.route?.route?.coordinates ?: return emptyList()
        val rendered = if (lon != null && lat != null) RouteMonitor.match(points,lon,lat,monitor.maxProgress) else null
        val consumed = if (rendered != null && rendered.distance <= 45) minOf(monitor.maxProgress,rendered.along) else monitor.maxProgress
        return RouteLineGeometry.remaining(points,consumed)
    }
    private var noticeRoute: RouteParser.Route?=null
    private var noticeFixNanos=Long.MIN_VALUE
    private var noticeStep=-1
    private var noticeValue=false
    fun nearManeuver(): Boolean {
        val route=state?.route?.route ?: return false
        val fix=latest?.takeIf { it.hasAccuracy() } ?: return false
        val index=progress?.step ?: if(route.steps.size>1) 1 else 0
        val age=(android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1000000
        if(age !in 0L..15000L) return false
        if(noticeRoute === route && noticeFixNanos==fix.elapsedRealtimeNanos && noticeStep==index) return noticeValue
        val step=route.steps.getOrNull(index) ?: return false
        val reference=route.steps.take(index).sumOf { it.distance }
        val turn=RouteMonitor.match(route.coordinates,step.location.longitude,step.location.latitude,reference) ?: return false
        val match=RouteMonitor.match(route.coordinates,fix.longitude,fix.latitude,monitor.maxProgress) ?: return false
        noticeRoute=route;noticeFixNanos=fix.elapsedRealtimeNanos;noticeStep=index
        noticeValue=ManeuverNotice.visible(turn.along,match.along,match.distance,fix.accuracy.toDouble(),age)
        return noticeValue
    }
    private var generation = 0
    private var fetching = false
    private var lastRequest = 0L
    private var lastFixNanos = -1L
    private val arrival = ArrivalCountdown()
    private var arrivalTick: Runnable? = null
    private fun resetArrival() {
        arrivalTick?.let { handler.removeCallbacks(it) };arrivalTick = null;arrival.reset()
    }
    private fun scheduleArrival() {
        if (arrivalTick != null) return
        val routeGeneration = generation
        val tick = object : Runnable {
            override fun run() {
                val current = state
                if (routeGeneration != generation || current == null || arrival.deadline == null) { if (arrivalTick === this) arrivalTick=null;return }
                val now = android.os.SystemClock.elapsedRealtime()
                if (arrival.due(now)) { stop(true);return }
                state = current.copy(message="Llegaste · terminando en ${arrival.secondsLeft(now)} s")
                emit()
                if (routeGeneration == generation && state != null && arrival.deadline != null) handler.postDelayed(this,1000)
            }
        }
        arrivalTick=tick;tick.run()
    }
    fun initialize(ctx: Context) {
        if (context != null) return
        context = ctx.applicationContext
        CameraPreferences.reload(ctx)
        androidx.core.content.ContextCompat.registerReceiver(ctx.applicationContext,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context,intent: android.content.Intent) {
                    try { CameraPreferences.receive(context,intent);emit() } catch (error: Exception) { CrashLog.save(context,error) }
                }
            },android.content.IntentFilter(CameraPreferences.ACTION),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val file = java.io.File(ctx.filesDir,"navigation-session.json")
            if (!file.exists() || System.currentTimeMillis()-file.lastModified() > 21600000) return
            val saved = JSONObject(file.readText())
            val route = saved.optString("json").takeIf { it.isNotBlank() }?.let { RoutingClient.decode(it) }
            state = State(saved.getDouble("lat"),saved.getDouble("lon"),saved.getString("name"),route,route?.let { listOf(it)+it.alternatives }.orEmpty(),kind=DestinationKind.restore(saved.optString("kind"),saved.getString("name")),vehicleLat=saved.optDouble("vehicleLat",saved.getDouble("lat")),vehicleLon=saved.optDouble("vehicleLon",saved.getDouble("lon")),entrance=saved.optBoolean("entrance",false),merchant=saved.optString("merchant"),unit=saved.optString("unit"),arrivalSpoken=saved.optBoolean("arrivalSpoken",false))
        } catch (_: Exception) { }
    }
    fun observe(ctx: Context,listener: (State?) -> Unit) { initialize(ctx);listeners += listener;listener(state) }
    fun remove(listener: (State?) -> Unit) { listeners -= listener }
    private fun emit() { listeners.toList().forEach { listener ->
        try { listener(state) } catch (error: Exception) { context?.let { CrashLog.save(it,error) } }
    } }
    fun start(ctx: Context,lat: Double,lon: Double,name: String,route: RoutingClient.Result? = null,options: List<RoutingClient.Result> = emptyList(),fix: Location? = null,kind: DestinationKind = DestinationKind.fromName(name),vehicleLat: Double=lat,vehicleLon: Double=lon,entrance: Boolean=false,merchant: String="",unit: String="") {
        initialize(ctx)
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        val old = state
        if (old != null && old.lat == lat && old.lon == lon && old.vehicleLat==vehicleLat && old.vehicleLon==vehicleLon && route == null) { emit();if (old.route == null && !fetching) request();return }
        generation++;fetching = false;lastRequest = 0;voiceCues.reset();NavigationVoice.cancel(keepAnnouncements=true);monitor.reset();visualTracker.reset();visualFix = null;resetArrival();progress = null;arrived = false
        state = State(lat,lon,name,route,if (options.isNotEmpty()) options else route?.let { listOf(it)+it.alternatives }.orEmpty(),if (route == null) "Calculando ruta…" else "",kind=kind,vehicleLat=vehicleLat.takeIf { it.isFinite()&&it in -90.0..90.0 } ?: lat,vehicleLon=vehicleLon.takeIf { it.isFinite()&&it in -180.0..180.0 } ?: lon,entrance=entrance,merchant=merchant,unit=unit)
        if (fix != null) latest = Location(fix)
        if (route != null && latest != null) { lastFixNanos = -1;onLocation(latest!!) }
        persist();emit()
        if (route == null) request()
    }
    fun updateSpeechDetails(name: String,merchant: String,unit: String) {
        val current=state?.takeIf { it.name==name } ?: return
        val updated=current.copy(merchant=merchant.ifBlank { current.merchant },unit=unit.ifBlank { current.unit })
        if(updated!=current) { state=updated;persist() }
    }
    private fun arrivalText(ctx: Context,current: State)=if(VoicePreferences.read(ctx).arrivalDetails)
        DeliverySpeech.arrival(current.name,current.kind,current.merchant,current.unit,current.route?.json) else "Llegaste a tu destino."
    fun stop(atDestination: Boolean = false) {
        if (state == null) return
        if(!atDestination || state?.arrivalSpoken!=true) context?.let { ctx ->
            NavigationVoice.speak(ctx,if(atDestination) arrivalText(ctx,state!!) else "Navegación terminada",finish=true)
        }
        voiceCues.reset()
        generation++;fetching = false;state = null;progress = null;arrived = atDestination;monitor.reset();visualTracker.reset();visualFix = null;resetArrival()
        context?.let { java.io.File(it.filesDir,"navigation-session.json").delete() }
        emit()
    }
    fun onLocation(fix: Location) {
        if (!fix.hasAccuracy() || !fix.accuracy.isFinite() || fix.accuracy < 0 || fix.accuracy > 35 || android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos !in 0L..15000000000L) return
        val previous = latest
        if (previous != null && fix.elapsedRealtimeNanos < previous.elapsedRealtimeNanos) return
        // Do not alternate a fresh, accurate GPS fix with a coarser network position.
        if (fix.provider != android.location.LocationManager.GPS_PROVIDER && previous?.provider == android.location.LocationManager.GPS_PROVIDER &&
            previous.hasAccuracy() && previous.accuracy <= 25 && fix.time-previous.time in 0L..5000L) return
        if (fix.elapsedRealtimeNanos == lastFixNanos) return
        lastFixNanos = fix.elapsedRealtimeNanos;latest = Location(fix)
        val reliableSpeed = fix.hasSpeed() && fix.hasAccuracy() && fix.accuracy<=25 && (!fix.hasSpeedAccuracy() || fix.speedAccuracyMetersPerSecond<=3)
        SpeedMotion.set(if (reliableSpeed) fix.speed.toDouble()*2.2369362921 else null)
        val trustedBearing = fix.hasBearing() && reliableSpeed && fix.speed >= 2.5 && (!fix.hasBearingAccuracy() || fix.bearingAccuracyDegrees <= 30)
        if (trustedBearing) {
            val delta = (fix.bearing-bearing+540)%360-180
            bearing = (bearing+delta*.4+360)%360
        }
        val current = state ?: return
        val route = current.route ?: run { if (!fetching) request();return }
        val result = monitor.evaluate(route.route.coordinates,fix.longitude,fix.latitude,
            if (fix.hasAccuracy()) fix.accuracy.toDouble() else 100.0,
            if (fix.hasSpeed()) fix.speed.toDouble() else 0.0,if (trustedBearing) fix.bearing.toDouble() else null,fix.elapsedRealtimeNanos/1000000)
        visualTracker.update(route.route.coordinates,fix.longitude,fix.latitude,
            if (fix.hasAccuracy()) fix.accuracy.toDouble() else 100.0,
            if (fix.hasSpeed()) fix.speed.toDouble() else 0.0,if (trustedBearing) fix.bearing.toDouble() else null)?.let { visual ->
            visualFix = Location(fix).apply { longitude = visual.coordinate.longitude;latitude = visual.coordinate.latitude;bearing = visual.heading.toFloat() }
        }
        val match = result.first ?: return
        val remaining = (match.total-monitor.maxProgress).coerceAtLeast(0.0)
        val step = ManeuverProgress.next(route.route.steps,monitor.maxProgress)
        route.route.steps.getOrNull(step)?.let { maneuver ->
            voiceCues.maneuver(route.json,step,maneuver.instruction,maneuver.road,
                route.route.steps.take(step).sumOf { it.distance.coerceAtLeast(0.0) }-monitor.maxProgress,
                if(reliableSpeed) fix.speed.toDouble() else 0.0,android.os.SystemClock.elapsedRealtime())?.let { text -> context?.let { NavigationVoice.speak(it,text) } }
        }
        progress = Progress(step,remaining,if (match.total > 0) route.duration*(remaining/match.total).coerceIn(0.0,1.0) else 0.0)
        val distance = FloatArray(1);Location.distanceBetween(fix.latitude,fix.longitude,current.vehicleLat,current.vehicleLon,distance)
        val previouslyArriving = arrival.deadline != null
        val eligible = distance[0]<25 && remaining<60 && fix.hasSpeed() && fix.speed<3 && fix.hasAccuracy() && fix.accuracy<=25
        val departed = distance[0]>50 && fix.hasAccuracy() && fix.accuracy<=25
        arrival.observe(android.os.SystemClock.elapsedRealtime(),eligible,departed)
        if (previouslyArriving && arrival.deadline == null) {
            resetArrival();state = current.copy(message="");emit()
        }
        if (arrival.deadline != null) {
            if(!current.arrivalSpoken) {
                state=state?.copy(arrivalSpoken=true);persist()
                context?.let { NavigationVoice.speak(it,arrivalText(it,state ?: current)) }
            }
            scheduleArrival();return
        }
        if (result.second && !fetching && System.currentTimeMillis()-lastRequest >= 8000) request()
    }
    fun request() {
        val ctx = context ?: return;val current = state ?: return
        if (fetching || current.route == null && lastRequest > 0 && System.currentTimeMillis()-lastRequest < 8000) return
        val origin = latest ?: run { state = current.copy(message="Esperando tu ubicación para calcular la ruta");emit();return }
        resetArrival()
        if(current.route!=null) NavigationVoice.speak(ctx,"Recalculando ruta")
        fetching = true;lastRequest = System.currentTimeMillis()
        val request = ++generation
        state = current.copy(loading=true,message=if (current.route == null) "Calculando ruta…" else "Recalculando ruta…");emit()
        val token = ctx.getSharedPreferences("mapa_flotante",Context.MODE_PRIVATE).getString("mapbox_token","").orEmpty()
        Thread {
            try {
                val heading = if (origin.hasBearing() && origin.hasSpeed() && origin.speed >= 2.5) origin.bearing.toDouble() else null
                val route = RoutingClient.fetch(origin.longitude,origin.latitude,current.vehicleLon,current.vehicleLat,token,heading)
                handler.post {
                    if (request != generation || state == null) return@post
                    fetching = false;monitor.reset();visualTracker.reset();visualFix = null;progress = null;resetArrival()
                    state = current.copy(route=route,options=listOf(route)+route.alternatives,loading=false,message="")
                    lastFixNanos = -1;latest?.let { onLocation(it) }
                    persist();emit()
                }
            } catch (_: Exception) {
                handler.post {
                    if (request != generation || state == null) return@post
                    fetching = false
                    state = current.copy(loading=false,message="No se pudo actualizar la ruta. Se reintentará con conexión.");emit()
                }
            }
        }.start()
    }
    private fun persist() {
        val ctx = context ?: return;val current = state ?: return
        try {
            val routes = JSONArray()
            val chosen = current.route
            if (chosen != null) {
                routes.put(JSONObject(chosen.json).getJSONArray("routes").getJSONObject(0))
                current.options.filter { it.route.coordinates != chosen.route.coordinates }.take(2).forEach { routes.put(JSONObject(it.json).getJSONArray("routes").getJSONObject(0)) }
            }
            val json = if (routes.length() > 0) JSONObject().put("code","Ok").put("routes",routes).put("drivefly_traffic",chosen?.trafficAware == true).toString() else ""
            val file = java.io.File(ctx.filesDir,"navigation-session.json")
            val temp = java.io.File(ctx.filesDir,"navigation-session.tmp")
            temp.writeText(JSONObject().put("lat",current.lat).put("lon",current.lon).put("name",current.name).put("kind",current.kind.name).put("vehicleLat",current.vehicleLat).put("vehicleLon",current.vehicleLon).put("entrance",current.entrance).put("merchant",current.merchant).put("unit",current.unit).put("arrivalSpoken",current.arrivalSpoken).put("json",json).toString())
            temp.renameTo(file)
        } catch (_: Exception) { }
    }
}
