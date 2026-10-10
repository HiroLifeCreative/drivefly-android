package com.hirolifecreative.floatingmap

import android.graphics.*
import android.view.View
import android.widget.FrameLayout
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import kotlin.math.*

/** Shared map-projected maneuver, arrow and road label. Never intercepts map gestures. */
object ReferenceNavigationOverlay {
    private val controllers=java.util.IdentityHashMap<MapView,Controller>()
    fun attach(map: MapView,following: ()->Boolean) { if(!controllers.containsKey(map)) controllers[map]=Controller(map,following) }
    fun hasBubble(map: MapView)=controllers[map]?.bubble ?: false
    fun visibilityListener(map: MapView,listener: (Boolean)->Unit) { controllers[map]?.listener=listener }
    private class Controller(val map: MapView,val following: ()->Boolean) {
        var bubble=false
        var listener: ((Boolean)->Unit)?=null
        private val artwork=Artwork(map,following) { visible -> if(bubble!=visible) { bubble=visible;listener?.invoke(visible) } }
        private val speed: (Double?)->Unit={ artwork.invalidate() }
        private val camera=map.mapboxMap.subscribeCameraChanged { artwork.invalidate() }
        private val navigation: (NavigationSession.State?)->Unit={ state -> artwork.visibility=if(state?.route!=null) View.VISIBLE else View.GONE;if(state?.route==null&&bubble) { bubble=false;listener?.invoke(false) };artwork.invalidate() }
        private val detach=object: View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { artwork.invalidate() }
            override fun onViewDetachedFromWindow(v: View) { camera.cancel();SpeedMotion.remove(speed);NavigationSession.remove(navigation);map.removeOnAttachStateChangeListener(this);map.removeView(artwork);controllers.remove(map);listener=null }
        }
        init { map.addView(artwork,FrameLayout.LayoutParams(-1,-1));map.addOnAttachStateChangeListener(detach);NavigationSession.observe(map.context,navigation);SpeedMotion.observe(speed) }
    }
    private class Artwork(val map: MapView,val following: ()->Boolean,val visible: (Boolean)->Unit): View(map.context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val path=Path()
        private val density=resources.displayMetrics.density
        init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        private fun pixel(c: RouteParser.Coordinate)=map.mapboxMap.pixelForCoordinate(Point.fromLngLat(c.longitude,c.latitude))
        override fun onDraw(canvas: Canvas) {
            val state=NavigationSession.state;val route=state?.route?.route
            if(route==null||width<100||height<100) { visible(false);return }
            val stepIndex=NavigationSession.progress?.step ?: if(route.steps.size>1) 1 else 0
            val step=route.steps.getOrNull(stepIndex) ?: run { visible(false);return }
            val d=density*min(1f,width/(320f*density))
            val turn=RouteMonitor.match(route.coordinates,step.location.longitude,step.location.latitude)
            val tip=pixel(step.location)
            val onScreen=tip.x.isFinite()&&tip.y.isFinite()&&tip.x in 30.0*d..width-30.0*d&&tip.y in 90.0*d..height*.85
            if(turn!=null&&onScreen&&NavigationSession.nearManeuver()) {
                val a=RouteLineGeometry.pointAt(route.coordinates,max(0.0,turn.along-7))
                val b=RouteLineGeometry.pointAt(route.coordinates,turn.along+9)
                if(a!=null&&b!=null) {
                    // Keep the arrow compact even with a close camera and strong perspective.
                    fun compact(point: com.mapbox.maps.ScreenCoordinate,limit: Double): com.mapbox.maps.ScreenCoordinate {
                        val dx=point.x-tip.x;val dy=point.y-tip.y
                        val factor=min(1.0,limit/hypot(dx,dy).coerceAtLeast(1.0))
                        return com.mapbox.maps.ScreenCoordinate(tip.x+dx*factor,tip.y+dy*factor)
                    }
                    val head=compact(pixel(b),28.0*d)
                    val approach=compact(pixel(a),22.0*d)
                    path.reset();path.moveTo(approach.x.toFloat(),approach.y.toFloat());path.lineTo(tip.x.toFloat(),tip.y.toFloat());path.lineTo(head.x.toFloat(),head.y.toFloat())
                    paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=7*d;paint.color=Color.parseColor("#005BC4");canvas.drawPath(path,paint)
                    paint.strokeWidth=4*d;paint.color=Color.WHITE;canvas.drawPath(path,paint)
                    val angle=atan2(head.y-tip.y,head.x-tip.x);val length=min(8.0*d,hypot(head.x-tip.x,head.y-tip.y)*.55);val wing=length*.48
                    path.reset();path.moveTo(head.x.toFloat(),head.y.toFloat())
                    path.lineTo((head.x-length*cos(angle)+wing*sin(angle)).toFloat(),(head.y-length*sin(angle)-wing*cos(angle)).toFloat())
                    path.lineTo((head.x-length*cos(angle)-wing*sin(angle)).toFloat(),(head.y-length*sin(angle)+wing*cos(angle)).toFloat());path.close()
                    paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawPath(path,paint)
                }
            }
            val canBubble=onScreen&&NavigationSession.nearManeuver()&&state.message.isBlank()&&step.instruction.isNotBlank()
            visible(canBubble)
            if(canBubble) {
                val instruction=shortInstruction(step.instruction)
                paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=17*d
                val maxWidth=min(width*.72f,310*d);val text=android.text.TextUtils.ellipsize(instruction,android.text.TextPaint(paint),maxWidth-26*d,android.text.TextUtils.TruncateAt.END).toString()
                val w=min(maxWidth,paint.measureText(text)+26*d);val h=42*d
                val x=(tip.x.toFloat()-w-10*d).coerceIn(10*d,width-w-10*d);val y=(tip.y.toFloat()-h-18*d).coerceAtLeast(10*d)
                val rect=RectF(x,y,x+w,y+h)
                paint.style=Paint.Style.FILL;paint.color=Color.parseColor("#250D0638");canvas.drawRoundRect(RectF(x+2*d,y+4*d,x+w+2*d,y+h+4*d),14*d,14*d,paint)
                path.reset();path.moveTo(x+w-25*d,y+h-4*d);path.lineTo(tip.x.toFloat(),tip.y.toFloat());path.lineTo(x+w-5*d,y+h-14*d);path.close()
                paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=6*d;canvas.drawPath(path,paint);canvas.drawRoundRect(rect,14*d,14*d,paint)
                paint.style=Paint.Style.FILL;paint.color=Color.parseColor(if(Appearance.dark(context)) "#007BAE" else "#4821F0");canvas.drawPath(path,paint);canvas.drawRoundRect(rect,14*d,14*d,paint)
                paint.color=Color.WHITE;canvas.drawText(text,x+13*d,y+h/2-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2,paint)
            }
            drawAlternatives(canvas,state,d)
            drawSpeed(canvas,state,d)
            drawLanes(canvas,step,route,turn,d)
            if(following()) {
                val road=route.steps.getOrNull((stepIndex-1).coerceAtLeast(0))?.road.orEmpty()
                if(road.isNotBlank()) {
                    val puckSize=64*density*CameraPreferences.current(context).puckSizePercent/100f
                    paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=14*d
                    val label=android.text.TextUtils.ellipsize(road,android.text.TextPaint(paint),width*.68f,android.text.TextUtils.TruncateAt.END).toString()
                    val w=paint.measureText(label)+24*d;val top=height.toFloat()*NavigationScreenAnchor.FRACTION.toFloat()+puckSize*.5f+3*d
                    if(top+28*d<height-8*d) {
                        val rect=RectF((width-w)/2,top,(width+w)/2,top+28*d)
                        paint.color=Color.parseColor(if(Appearance.dark(context)) "#DD008ABE" else "#EFFFFFFF");canvas.drawRoundRect(rect,14*d,14*d,paint)
                        paint.color=Color.parseColor(if(Appearance.dark(context)) "#FFFFFF" else "#15085A");canvas.drawText(label,rect.left+12*d,top+14*d-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2,paint)
                    }
                }
            }
        }
        private fun drawLanes(canvas: Canvas,step: RouteParser.Step,route: RouteParser.Route,turn: RouteMonitor.Match?,d: Float) {
            if(!following() || !MapExperience.read(context).laneGuidance || step.lanes.isEmpty() || turn==null) return
            val fix=NavigationSession.latest?.takeIf { it.hasAccuracy()&&it.accuracy<=35&&System.currentTimeMillis()-it.time<=15000 } ?: return
            val match=RouteMonitor.match(route.coordinates,fix.longitude,fix.latitude) ?: return
            if(match.distance>35 || turn.along-match.along !in -5.0..800.0) return
            val available=width-145*d
            if(available<75*d) return
            val w=min(available,step.lanes.size*30*d+16*d);val laneWidth=(w-16*d)/step.lanes.size
            val rect=RectF(9*d,9*d,9*d+w,73*d)
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=Color.parseColor("#E5101117");canvas.drawRoundRect(rect,12*d,12*d,paint)
            paint.color=Color.parseColor("#C8CAD2");paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=10*d;paint.textAlign=Paint.Align.LEFT
            canvas.drawText("Carriles",rect.left+9*d,rect.top+14*d,paint)
            step.lanes.forEachIndexed { i,lane ->
                val cx=rect.left+8*d+laneWidth*(i+.5f);val bottom=rect.bottom-9*d;val top=rect.top+25*d
                val recommended=LaneGuidance.recommended(step.lanes,i)
                val indications=if(recommended&&lane.routeDirection!=null) listOf(lane.routeDirection) else lane.directions
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f*d;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
                paint.color=Color.parseColor(if(recommended) "#69E879" else if(lane.valid) "#CFD6E0" else "#657180")
                if(indications.isEmpty()) canvas.drawLine(cx-3*d,bottom,cx+3*d,bottom,paint)
                indications.forEach { direction ->
                    val side=when { direction.contains("left") || direction=="uturn" -> -1;direction.contains("right") -> 1;else -> 0 }
                    val endX=cx+side*min(laneWidth*.32f,8*d);val endY=if(direction=="uturn") bottom-5*d else top
                    path.reset();path.moveTo(cx,bottom)
                    if(side==0) path.lineTo(endX,endY)
                    else if(direction=="uturn") { path.lineTo(cx,top+7*d);path.cubicTo(cx,top-4*d,endX,top-4*d,endX,top+7*d);path.lineTo(endX,endY) }
                    else { path.lineTo(cx,top+12*d);path.quadTo(cx,top,endX,top); }
                    canvas.drawPath(path,paint)
                    val angle=if(direction=="uturn") Math.PI/2 else if(side==0) -Math.PI/2 else if(side<0) Math.PI else 0.0
                    val size=4*d
                    path.reset();path.moveTo((endX-size*cos(angle-.65)).toFloat(),(endY-size*sin(angle-.65)).toFloat());path.lineTo(endX,endY);path.lineTo((endX-size*cos(angle+.65)).toFloat(),(endY-size*sin(angle+.65)).toFloat());canvas.drawPath(path,paint)
                }
            }
            paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.LEFT
        }
        private fun drawSpeed(canvas: Canvas,state: NavigationSession.State,d: Float) {
            val fix=NavigationSession.latest?.takeIf { it.hasAccuracy() && it.accuracy <= 35 && System.currentTimeMillis()-it.time <= 15000 }
            val limit=fix?.let { RouteAnnotationData.limit(state.route,it.longitude,it.latitude) }
            val warning=fix!=null&&SpeedWarning.active(SpeedMotion.mph,limit)
            val pulse=if(warning) SpeedWarning.pulse(android.os.SystemClock.elapsedRealtime()) else 1f
            val w=if(limit==null) 59*d else 116*d;val h=64*d
            val x=width-w-9*d;val y=9*d
            paint.style=Paint.Style.FILL;paint.shader=null;paint.color=Color.parseColor("#E5101117");canvas.drawRoundRect(RectF(x,y,x+w,y+h),12*d,12*d,paint)
            if(limit!=null) {
                val rect=RectF(x+4*d,y+4*d,x+55*d,y+h-4*d)
                paint.color=Color.parseColor("#EEEEEE");canvas.drawRoundRect(rect,7*d,7*d,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.color=Color.parseColor("#212126");canvas.drawRoundRect(RectF(rect.left+2*d,rect.top+2*d,rect.right-2*d,rect.bottom-2*d),5*d,5*d,paint)
                paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=29*d;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
                canvas.drawText(kotlin.math.round(limit).toInt().toString(),rect.centerX(),y+42*d,paint)
            }
            val cx=if(limit==null) x+w/2 else x+86*d
            paint.textAlign=Paint.Align.CENTER;paint.textSize=27*d;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            paint.color=if(warning) Color.argb((160+95*pulse).toInt(),255,59,48) else Color.WHITE
            if(warning) paint.setShadowLayer((5+7*pulse)*d,0f,0f,Color.argb((80+120*pulse).toInt(),255,45,35))
            canvas.drawText(SpeedMotion.mph?.let { kotlin.math.round(it).toInt().toString() } ?: "—",cx,y+33*d,paint)
            paint.textSize=13*d;paint.color=if(warning) Color.argb((160+95*pulse).toInt(),255,95,80) else Color.parseColor("#C8CAD2")
            canvas.drawText("mph",cx,y+52*d,paint)
            paint.clearShadowLayer();paint.textAlign=Paint.Align.LEFT
            if(warning&&isAttachedToWindow&&isShown) postInvalidateOnAnimation()
        }
        private fun drawAlternatives(canvas: Canvas,state: NavigationSession.State,d: Float) {
            if(!NavigationSession.nearManeuver()) return
            val primary=state.route ?: return
            val alternatives=state.options.filter { it.route.coordinates!=primary.route.coordinates }.take(1)
            for(alt in alternatives) {
                var anchor: RouteParser.Coordinate?=null
                val stride=(alt.route.coordinates.size/40).coerceAtLeast(1)
                for(i in alt.route.coordinates.indices step stride) {
                    val c=alt.route.coordinates[i];val pixel=pixel(c)
                    if(pixel.x !in 22.0*d..width-22.0*d || pixel.y !in 130.0*d..height*.51) continue
                    if((RouteMonitor.match(primary.route.coordinates,c.longitude,c.latitude)?.distance ?: 0.0)<18) continue
                    anchor=c;break
                }
                val c=anchor ?: continue;val point=pixel(c)
                val road=alt.route.steps.firstOrNull { it.road.isNotBlank()&&primary.route.steps.none { a->a.road==it.road } }?.road ?: alt.route.steps.firstOrNull { it.road.isNotBlank() }?.road ?: "Ruta alternativa"
                val delta=alt.duration-primary.duration
                val arrival=if(abs(delta)<=60) "Llegada similar" else if(delta>0) "+${ceil(delta/60).toInt()} min" else "-${ceil(-delta/60).toInt()} min"
                val lessTraffic=alt.annotation.meanCongestion?.let { congestion->primary.annotation.meanCongestion?.let { congestion+10<it } }==true
                paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=13*d
                val w=min(width*.53f,180*d);val h=if(lessTraffic) 72*d else 53*d
                val x=(point.x.toFloat()-w-9*d).coerceIn(9*d,width-w-9*d);val y=(point.y.toFloat()+12*d).coerceAtMost(height*.5f-h)
                val rect=RectF(x,y,x+w,y+h)
                paint.style=Paint.Style.FILL;paint.color=Color.parseColor(if(Appearance.dark(context)) "#C9006493" else "#DBCFDBFF");canvas.drawRoundRect(rect,12*d,12*d,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3*d;paint.color=Color.parseColor("#CCFFFFFF");canvas.drawRoundRect(rect,12*d,12*d,paint)
                path.reset();path.moveTo(x+w-20*d,y+2*d);path.lineTo(point.x.toFloat(),point.y.toFloat());path.lineTo(x+w-3*d,y+14*d);canvas.drawPath(path,paint)
                paint.style=Paint.Style.FILL;paint.color=Color.parseColor(if(Appearance.dark(context)) "#FFFFFF" else "#120858")
                val title=android.text.TextUtils.ellipsize(road,android.text.TextPaint(paint),w-20*d,android.text.TextUtils.TruncateAt.END).toString()
                canvas.drawText(title,x+10*d,y+20*d,paint);paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);canvas.drawText(arrival,x+10*d,y+39*d,paint)
                if(lessTraffic) canvas.drawText("Menos tráfico",x+10*d,y+58*d,paint)
            }
        }
    }
    internal fun shortInstruction(text: String): String=when {
        text.startsWith("Gira a la derecha",true)->"Gira a la derecha"
        text.startsWith("Gira a la izquierda",true)->"Gira a la izquierda"
        else->text.trim()
    }
}
