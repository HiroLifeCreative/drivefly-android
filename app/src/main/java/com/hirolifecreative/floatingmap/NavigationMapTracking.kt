package com.hirolifecreative.floatingmap

import android.view.Choreographer
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.view.View
import android.widget.FrameLayout
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.locationcomponent.location

/** A screen marker cannot move with GPS or the native location layer's render timing. */
object NavigationMapTracking {
    private val controllers = java.util.IdentityHashMap<MapView,Controller>()
    fun attach(map: MapView,following: () -> Boolean,onPosition: (Point) -> Unit) {
        controllers.remove(map)?.dispose()
        controllers[map] = Controller(map,following,onPosition)
    }
    fun update(map: MapView,fix: Location) { controllers[map]?.update(fix) }

    private class Controller(val map: MapView,val following: () -> Boolean,val onPosition: (Point) -> Unit) {
        private val marker = FixedNavigationMarker(map.context)
        private val frames = Choreographer.getInstance()
        private var scheduled = false
        private var lastFrame = 0L
        private var points = emptyList<RouteParser.Coordinate>()
        private var roadMode = false
        private var targetAlong = 0.0
        private var targetLon = 0.0
        private var targetLat = 0.0
        private var angleTarget = 0.0
        private val distanceMotion = SmoothValue()
        private val longitudeMotion = SmoothValue()
        private val latitudeMotion = SmoothValue()
        private val bearingMotion = SmoothValue(smoothTime=.9)
        private val turnMotion = SmoothValue(smoothTime=.55)
        private val pitchMotion = SmoothValue(60.0,smoothTime=.8)
        private val zoomMotion = SmoothValue(CameraConfig.BASE_ZOOM,smoothTime=1.2)
        private fun desiredPitch() = if (NavigationSession.threeD) CameraPreferences.current(map.context).tilt.toDouble() else 0.0
        private val frame = Choreographer.FrameCallback { time -> advance(time) }
        private var routeJson: String? = null
        private var current: Point? = null
        private var heading = 0.0
        private var target: Point? = null
        private var targetBearing = 0.0
        private val speedListener: (Double?) -> Unit = {
            if (target != null && CameraPreferences.current(map.context).speedZoomEnabled && mode()) schedule()
        }
        private val size get() = (64*map.resources.displayMetrics.density*CameraPreferences.current(map.context).puckSizePercent/100f).toInt()
        private val camera=map.mapboxMap.subscribeCameraChanged { scaleMarker() }
        private fun scaleMarker() {
            val scale=NavigationZoomScale.factor(map.mapboxMap.cameraState.zoom).toFloat()
            marker.pivotX=marker.width/2f;marker.pivotY=marker.height/2f
            marker.scaleX=scale;marker.scaleY=scale
            val config=CameraPreferences.current(map.context)
            marker.tilt=PuckOrientation.viewTilt(config.puckTilt,map.mapboxMap.cameraState.pitch,config.tilt)
            marker.perspective=PuckOrientation.perspective(map.mapboxMap.cameraState.pitch,config.tilt)
        }
        private val layout = View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> placeMarker() }
        private val detach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { placeMarker() }
            override fun onViewDetachedFromWindow(v: View) { controllers.remove(map);dispose() }
        }
        init {
            marker.visibility = View.GONE
            marker.isClickable = false
            marker.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            map.addView(marker,FrameLayout.LayoutParams(size,size))
            ReferenceNavigationOverlay.attach(map,following)
            map.addOnLayoutChangeListener(layout)
            map.addOnAttachStateChangeListener(detach)
            placeMarker()
            SpeedMotion.observe(speedListener)
        }
        private fun placeMarker() {
            val left = (map.width*.5-size*.5).toInt()
            val top = (map.height*NavigationScreenAnchor.FRACTION-size*.5).toInt()
            scaleMarker()
            val previous = marker.layoutParams as? FrameLayout.LayoutParams
            if (previous?.leftMargin == left && previous.topMargin == top && previous.width==size && previous.height==size) return
            marker.layoutParams = FrameLayout.LayoutParams(size,size).apply { leftMargin = left;topMargin = top }
        }
        private fun mode(): Boolean {
            val active = NavigationSession.state != null && following()
            marker.visibility = if (active) View.VISIBLE else View.GONE
            val tilt=CameraPreferences.current(map.context).puckTilt
            if (marker.tag!=tilt) { marker.tag=tilt;marker.invalidate() }
            if (map.location.enabled == active) map.location.updateSettings { enabled = !active }
            if (active) { placeMarker();if (map.indexOfChild(marker) != map.childCount-1) marker.bringToFront() }
            return active
        }
        private fun cancelFrames() {
            frames.removeFrameCallback(frame);scheduled = false;lastFrame = 0L
        }
        private fun schedule() {
            if (scheduled) return
            scheduled = true;lastFrame = 0L;frames.postFrameCallback(frame)
        }
        fun update(fix: Location) {
            if (!mode()) { cancelFrames();current = null;target = null;turnMotion.reset(0.0);marker.turn=0f;return }
            val route = NavigationSession.state?.route
            val point = Point.fromLngLat(fix.longitude,fix.latitude)
            val nextHeading = if (fix.hasBearing()) fix.bearing.toDouble() else NavigationSession.bearing
            if (target == point && targetBearing == nextHeading && routeJson == route?.json) { schedule();return }
            val previous = current
            val newRoute = routeJson != route?.json
            routeJson = route?.json;target = point;targetBearing = nextHeading
            val nextPoints = route?.route?.coordinates.orEmpty()
            val start = previous?.let { RouteMonitor.match(nextPoints,it.longitude(),it.latitude()) }
            val end = RouteMonitor.match(nextPoints,point.longitude(),point.latitude(),start?.along)
            val nextRoadMode = start != null && end != null && start.distance <= 3 && end.distance <= 3
            if (previous == null) {
                roadMode = false
                longitudeMotion.reset(point.longitude());latitudeMotion.reset(point.latitude());bearingMotion.reset(nextHeading)
                pitchMotion.reset(desiredPitch())
                zoomMotion.reset(SpeedZoom.target(CameraPreferences.current(map.context),SpeedMotion.mph))
                render(point,nextHeading)
            }
            if (nextRoadMode && (!roadMode || newRoute)) {
                distanceMotion.reset(start!!.along)
            } else if (!nextRoadMode && (roadMode || newRoute)) {
                longitudeMotion.reset(previous?.longitude() ?: point.longitude())
                latitudeMotion.reset(previous?.latitude() ?: point.latitude())
            }
            points = nextPoints;roadMode = nextRoadMode
            targetAlong = end?.along ?: 0.0;targetLon = point.longitude();targetLat = point.latitude()
            angleTarget = SmoothValue.nearestAngle(bearingMotion.value,nextHeading)
            schedule()
        }
        private fun advance(time: Long) {
            if (!mode()) { cancelFrames();current = null;target = null;turnMotion.reset(0.0);marker.turn=0f;return }
            val seconds = if (lastFrame == 0L) 1.0/60 else ((time-lastFrame)/1e9).coerceIn(.001,.05)
            lastFrame = time
            val coordinate = if (roadMode) RouteLineGeometry.pointAt(points,distanceMotion.advance(targetAlong,seconds)) else null
            val point = coordinate?.let { Point.fromLngLat(it.longitude,it.latitude) }
                ?: Point.fromLngLat(longitudeMotion.advance(targetLon,seconds),latitudeMotion.advance(targetLat,seconds))
            val bearing = bearingMotion.advance(angleTarget,seconds)
            val turnTarget=PuckTurnMotion.target(bearing,targetBearing,SpeedMotion.mph)
            marker.turn=turnMotion.advance(turnTarget,seconds).toFloat()
            val pitchTarget = desiredPitch()
            pitchMotion.advance(pitchTarget,seconds)
            val zoomTarget = SpeedZoom.target(CameraPreferences.current(map.context),SpeedMotion.mph)
            zoomMotion.advance(zoomTarget,seconds)
            render(point,(bearing%360+360)%360)
            val positionSettled = if (roadMode) distanceMotion.settled(targetAlong,.03)
                else longitudeMotion.settled(targetLon,.0000001) && latitudeMotion.settled(targetLat,.0000001)
            if (positionSettled && bearingMotion.settled(angleTarget,.02) && pitchMotion.settled(pitchTarget,.02) && zoomMotion.settled(zoomTarget,.001) && turnMotion.settled(0.0,.05)) {
                scheduled = false;lastFrame = 0L
            } else frames.postFrameCallback(frame)
        }
        private fun render(point: Point,bearing: Double) {
            current = point;heading = bearing
            DriveFlyMap.setRendered(map,point,bearing)
            val fix = Location("route-display").apply { longitude = point.longitude();latitude = point.latitude() }
            map.mapboxMap.setCamera(NavigationCameraProfile.options(map,fix,true,bearing=bearing).toBuilder().pitch(pitchMotion.value).zoom(zoomMotion.value).build())
            onPosition(point)
        }
        fun dispose() {
            camera.cancel()
            SpeedMotion.remove(speedListener)
            cancelFrames()
            map.removeOnLayoutChangeListener(layout)
            map.removeOnAttachStateChangeListener(detach)
            map.removeView(marker)
        }
    }
}

object NavigationScreenAnchor {
    const val FRACTION = .70
    const val BOTTOM_PADDING = .10
    const val TOP_PADDING = 2*FRACTION-1+BOTTOM_PADDING
}

/** Shared artwork for the fixed marker and native dark-mode location puck. */
internal object NavigationPuckArtwork {
    private val face=Path().apply {
        moveTo(32f,9f);cubicTo(36f,9f,49f,32f,51f,37f)
        cubicTo(56f,45f,48f,47f,43f,44f);lineTo(35f,39f)
        cubicTo(33f,38f,31f,38f,29f,39f);lineTo(21f,44f)
        cubicTo(16f,47f,8f,45f,13f,37f);cubicTo(15f,32f,28f,9f,32f,9f);close()
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val nightGradient=android.graphics.LinearGradient(16f,10f,45f,46f,intArrayOf(Color.parseColor("#66B2FF"),Color.parseColor("#007AFF"),Color.parseColor("#0057C2")),floatArrayOf(0f,.5f,1f),android.graphics.Shader.TileMode.CLAMP)
    private val gradient=android.graphics.LinearGradient(16f,10f,45f,46f,intArrayOf(Color.parseColor("#66B2FF"),Color.parseColor("#007AFF"),Color.parseColor("#0057C2")),floatArrayOf(0f,.5f,1f),android.graphics.Shader.TileMode.CLAMP)
    fun draw(canvas: Canvas,width: Float,height: Float,dark: Boolean,tilt: Float=0f,perspective: Float=1f,turn: Float=0f) {
        canvas.save();canvas.scale(width/64f,height/64f)
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=Color.parseColor("#220E0738")
        if(perspective>0f) { paint.alpha=(34*perspective).toInt();canvas.drawOval(7f,34f,57f,57f,paint);paint.alpha=255 }
        val haloTop=1f+12f*perspective;val haloBottom=63f-7f*perspective
        paint.color=Color.parseColor(if(dark) "#7700BDF2" else "#DDFFFFFF");canvas.drawOval(1f,haloTop,63f,haloBottom,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.65f;paint.color=Color.parseColor(if(dark) "#BB82E8FF" else "#E8DFDFE8");canvas.drawOval(1f,haloTop,63f,haloBottom,paint)
        paint.style=Paint.Style.FILL
        if(tilt!=0f) {
            val transform=android.graphics.Matrix();android.graphics.Camera().apply { rotateX(tilt);getMatrix(transform) }
            transform.preTranslate(-32f,-32f);transform.postTranslate(32f,32f);canvas.concat(transform)
        }
        canvas.rotate(turn,32f,32f)
        paint.color=Color.parseColor("#004AAB")
        if(perspective>0f) for(depth in 5 downTo 1) { canvas.save();canvas.translate(0f,depth*.7f*perspective);paint.alpha=if(depth==5) 70 else 255;canvas.drawPath(face,paint);canvas.restore() }
        paint.alpha=255;paint.shader=if(dark) nightGradient else gradient;canvas.drawPath(face,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f;paint.color=Color.parseColor("#6066B2FF");canvas.drawPath(face,paint)
        paint.style=Paint.Style.FILL;canvas.restore()
    }
}

/** Its layout never receives GPS coordinates. */
private class FixedNavigationMarker(context: android.content.Context) : View(context) {
    var turn=0f
        set(value) { if(kotlin.math.abs(value-field)>.01f) { field=value;invalidate() } }
    var tilt=0f
        set(value) { if(kotlin.math.abs(value-field)>.01f) { field=value;invalidate() } }
    var perspective=1f
        set(value) { if(kotlin.math.abs(value-field)>.001f) { field=value;invalidate() } }
    override fun onDraw(canvas: Canvas) {
        NavigationPuckArtwork.draw(canvas,width.toFloat(),height.toFloat(),Appearance.dark(context),tilt,perspective,turn)
    }
}
