package com.hirolifecreative.floatingmap

import android.graphics.*
import android.location.Location
import android.view.View
import android.widget.FrameLayout
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import kotlin.math.*

/** Destination stays at its geographic position and fades in only near an active destination. */
object DestinationMarker {
    private val controllers = java.util.IdentityHashMap<MapView,Controller>()
    fun set(map: MapView,point: Point?,compact: Boolean = false,kind: DestinationKind = DestinationKind.OTHER) {
        val controller = controllers[map] ?: Controller(map,compact).also { controllers[map]=it }
        controller.point=point;controller.kind=kind;controller.update()
    }
    private class Controller(val map: MapView,val compact: Boolean) {
        var point: Point? = null
        var kind=DestinationKind.OTHER
        private val pin = Pin(map.context)
        private val size = (44*map.resources.displayMetrics.density).roundToInt()
        private val navigation: (NavigationSession.State?) -> Unit = { update() }
        private val camera = map.mapboxMap.subscribeCameraChanged { update() }
        private val layout = View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> update() }
        private val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { update() }
            override fun onViewDetachedFromWindow(v: View) {
                camera.cancel();NavigationSession.remove(navigation);map.removeOnLayoutChangeListener(layout)
                map.removeOnAttachStateChangeListener(this);map.removeView(pin)
                controllers.remove(map)
            }
        }
        init {
            pin.visibility=View.GONE;pin.contentDescription="Destino"
            map.addView(pin,FrameLayout.LayoutParams(size,size))
            map.addOnLayoutChangeListener(layout);map.addOnAttachStateChangeListener(attach)
            NavigationSession.observe(map.context,navigation)
        }
        fun update() {
            val destination=point
            if (destination==null || map.width<size*2 || map.height<size*2) { pin.visibility=View.GONE;return }
            var opacity=1f
            if (NavigationSession.state != null) {
                val fix=NavigationSession.visualFix ?: NavigationSession.latest
                val distance=fix?.let {
                    val result=FloatArray(1)
                    Location.distanceBetween(it.latitude,it.longitude,destination.latitude(),destination.longitude(),result)
                    result[0].toDouble()
                }
                opacity=DestinationVisibility.alpha(distance)
            }
            if (opacity<=0f) { pin.visibility=View.GONE;return }
            val pixel=map.mapboxMap.pixelForCoordinate(destination)
            val x=pixel.x;val y=pixel.y
            // Never move the destination onto an edge: only its real projected coordinate.
            if (!x.isFinite() || !y.isFinite() || x<0 || y<0 || x>map.width || y>map.height) {
                pin.visibility=View.GONE;return
            }
            pin.appearance(Color.parseColor(NavigationColors.pin(map.context,NavigationSession.state?.kind ?: kind)))
            pin.contentDescription=when(NavigationSession.state?.kind ?: kind) { DestinationKind.PICKUP -> "Restaurante · recogida";DestinationKind.DELIVERY -> "Cliente · entrega";else -> "Destino" }
            val scale=NavigationZoomScale.factor(map.mapboxMap.cameraState.zoom).toFloat()
            // Scale around the tip so the destination remains on its exact coordinate.
            pin.pivotX=size/2f;pin.pivotY=size*42f/44f
            pin.scaleX=scale;pin.scaleY=scale
            pin.alpha=opacity
            pin.translationX=(x-size/2).toFloat();pin.translationY=(y-size*42.0/44.0).toFloat()
            pin.visibility=View.VISIBLE;pin.bringToFront();pin.invalidate()
        }
    }
    private class Pin(context: android.content.Context): View(context) {
        private var tint=Color.parseColor("#172A4B")
        private var dark: Boolean? = null
        private var front: Shader? = null
        private var side=Color.BLACK
        private val aperture=RadialGradient(21f,17f,7f,intArrayOf(Color.WHITE,Color.parseColor("#EEF2F6"),Color.parseColor("#BCC6D2")),floatArrayOf(0f,.65f,1f),Shader.TileMode.CLAMP)
        fun appearance(color: Int) {
            val night=Appearance.dark(context)
            if (color==tint && dark==night && front!=null) return
            tint=color;dark=night
            fun blend(amount: Float) = Color.rgb(
                (Color.red(color)+(255-Color.red(color))*amount).toInt(),
                (Color.green(color)+(255-Color.green(color))*amount).toInt(),
                (Color.blue(color)+(255-Color.blue(color))*amount).toInt())
            side=Color.rgb((Color.red(color)*.58f).toInt(),(Color.green(color)*.58f).toInt(),(Color.blue(color)*.58f).toInt())
            front=LinearGradient(7f,3f,36f,38f,intArrayOf(blend(if(night) .2f else .3f),color,side),floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
        }
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        // Bottom tip (22,42) anchors to the destination's actual map coordinate.
        private val outline=Path().apply {
            moveTo(22f,42f)
            cubicTo(18f,37f,7f,25f,7f,18f)
            cubicTo(7f,9.7f,13.7f,3f,22f,3f)
            cubicTo(30.3f,3f,37f,9.7f,37f,18f)
            cubicTo(37f,25f,26f,37f,22f,42f)
            close()
        }
        override fun onDraw(canvas: Canvas) {
            canvas.save();canvas.scale(width/44f,height/44f)
            // Small fixed-depth extrusion; the front tip remains exactly at (22,42).
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=Color.parseColor("#350A1724")
            canvas.drawOval(17f,40f,29f,44f,paint)
            paint.color=side
            for (layer in 6 downTo 1) {
                canvas.save();canvas.translate(layer*.45f,-layer*.3f)
                canvas.drawPath(outline,paint);canvas.restore()
            }
            paint.shader=front;canvas.drawPath(outline,paint);paint.shader=null
            paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f
            paint.color=if (dark==true) Color.parseColor("#608CA8DE") else Color.parseColor("#A0FFFFFF")
            canvas.drawPath(outline,paint)
            paint.style=Paint.Style.FILL;paint.shader=aperture
            canvas.drawCircle(22f,18f,5.2f,paint);paint.shader=null
            canvas.restore()
        }
    }
}
