package com.hirolifecreative.floatingmap
import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import kotlin.math.sin

/** Camera preferences stay available without loading the Maps SDK. */
class CameraSettingsActivity : Activity() {
    private var draft=CameraConfig()
    private val previewHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var resumed=false
    private var previewQueued=false
    private var updating=false
    private val sendPreview=Runnable { previewQueued=false;if(resumed) CameraPreferences.preview(this,draft) }
    private lateinit var preview: CameraPreview
    private lateinit var status: TextView
    private lateinit var speedToggle: DriveFlySwitch
    private lateinit var puckToggle: DriveFlySwitch
    private lateinit var adaptiveBody: LinearLayout
    private val sliders=mutableListOf<Pair<CameraSlider,(CameraConfig)->Int>>()
    private val labels=mutableListOf<Pair<TextView,(CameraConfig)->String>>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);CrashLog.install(this)
        val saved=CameraPreferences.reload(this)
        draft=state?.getString("cameraDraft")?.let { try { CameraConfigCodec.decode(org.json.JSONObject(it)) } catch (_: Exception) { saved } } ?: saved
        val ui=SettingsUi.Page(this,"Cámara y puck",save={ saveDraft() })
        val framing=ui.section("Encuadre")
        preview=CameraPreview(this).apply { config=draft }
        SettingsUi.body(framing).addView(preview,LinearLayout.LayoutParams(-1,SettingsUi.dp(this,130)))
        fun control(group: LinearLayout,title: String,min: Int,max: Int,step: Int,value: (CameraConfig)->Int,format: (CameraConfig)->String,change: (CameraConfig,Int)->CameraConfig) {
            val label=SettingsUi.text(this,"",13f,secondary=true);labels+=label to format
            SettingsUi.row(group,title,"sliders",trailing=label)
            val slider=CameraSlider(this,min,max,step,value(draft)) { number -> draft=change(draft,number).normalized();updateLabels(true) }
            slider.contentDescription=title;sliders+=slider to value
            SettingsUi.body(group).addView(slider,LinearLayout.LayoutParams(-1,SettingsUi.dp(this,44)))
        }
        control(framing,"Inclinación",0,75,1,{it.tilt},{"${it.tilt}°"},{c,v->c.copy(tilt=v)})
        control(framing,"Distancia",50,1000,5,{it.distancePercent},{"${it.distancePercent}%"},{c,v->c.copy(distancePercent=v)})
        ui.note("Más distancia muestra más camino. Los mapas abiertos reflejan la vista previa.")
        val adaptive=ui.section("Zoom automático")
        speedToggle=SettingsUi.toggle(adaptive,"Según velocidad","car",draft.speedZoomEnabled) { checked ->
            if(!updating) { draft=draft.copy(speedZoomEnabled=checked);updateLabels(true) }
        }
        adaptiveBody=SettingsUi.group(this);adaptive.addView(adaptiveBody)
        fun percent(v: Int)=if(v>0) "+$v%" else "$v%"
        control(adaptiveBody,"Velocidad baja",0,119,1,{it.slowMph},{"≤ ${it.slowMph} mph"},{c,v->c.copy(slowMph=v)})
        control(adaptiveBody,"Distancia a baja velocidad",-80,200,5,{it.slowDistanceChange},{percent(it.slowDistanceChange)},{c,v->c.copy(slowDistanceChange=v)})
        control(adaptiveBody,"Velocidad alta",1,120,1,{it.fastMph},{"> ${it.fastMph} mph"},{c,v->c.copy(fastMph=v)})
        control(adaptiveBody,"Distancia a alta velocidad",-80,200,5,{it.fastDistanceChange},{percent(it.fastDistanceChange)},{c,v->c.copy(fastDistanceChange=v)})
        ui.note("+ aleja y − acerca. Entre ambas velocidades se usa la distancia guardada.")
        val puck=ui.section("Tu puck")
        control(puck,"Tamaño",50,250,10,{it.puckSizePercent},{"${it.puckSizePercent}%"},{c,v->c.copy(puckSizePercent=v)})
        puckToggle=SettingsUi.toggle(puck,"Modelo 3D","layers",draft.nativePuck3D) { checked ->
            if(!updating) { draft=draft.copy(nativePuck3D=checked);updateLabels(true) }
        }
        control(puck,"Inclinación propia",0,60,1,{it.puckTilt},{"${it.puckTilt}°"},{c,v->c.copy(puckTilt=v)})
        ui.note("La inclinación del puck es independiente del mapa. En navegación, tu punto conserva su posición fija.")
        SettingsUi.row(ui.section("Restablecer"),"Valores predeterminados","gear") { draft=CameraConfig();updateLabels(true) }
        status=ui.note("");updateLabels(false)
    }
    private fun saveDraft() {
        try {
            previewHandler.removeCallbacks(sendPreview);previewQueued=false;CameraPreferences.save(this,draft)
            Toast.makeText(this,"Cámara guardada para ambos mapas",Toast.LENGTH_SHORT).show();finish()
        } catch (_: Exception) { status.text="No se pudo guardar. Inténtalo de nuevo." }
    }
    private fun updateLabels(changed: Boolean) {
        updating=true
        speedToggle.isChecked=draft.speedZoomEnabled;puckToggle.isChecked=draft.nativePuck3D
        adaptiveBody.visibility=if(draft.speedZoomEnabled) View.VISIBLE else View.GONE
        labels.forEach { (view,format)->view.text=format(draft) }
        sliders.forEach { (view,value)->view.setValue(value(draft)) }
        preview.config=draft;updating=false
        status.text=if(changed) "Vista previa · pulsa Guardar para conservar los cambios" else "Tus ajustes se aplican a ambos mapas"
        if(resumed&&!previewQueued) { previewQueued=true;previewHandler.postDelayed(sendPreview,100) }
    }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus);if(focus) SettingsUi.chrome(this) }
    override fun onResume() { super.onResume();resumed=true;CameraPreferences.preview(this,draft) }
    override fun onPause() { resumed=false;previewHandler.removeCallbacks(sendPreview);previewQueued=false;CameraPreferences.clearPreview(this);super.onPause() }
    override fun onSaveInstanceState(state: Bundle) { state.putString("cameraDraft",CameraConfigCodec.encode(draft).toString());super.onSaveInstanceState(state) }
}

/** Rounded slider with keyboard and accessibility support; no platform SeekBar visuals. */
internal class CameraSlider(context: android.content.Context,private val min: Int,private val max: Int,private val step: Int,
    initial: Int,private val change: (Int) -> Unit) : View(context) {
    private var value = initial
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { isFocusable = true;isClickable = true }
    fun setValue(number: Int) { value = number.coerceIn(min,max);invalidate() }
    private fun edit(number: Int) {
        val next = (min+((number-min).toDouble()/step).roundToInt()*step).coerceIn(min,max)
        if (next != value) { value = next;invalidate();change(value) }
    }
    override fun onDraw(canvas: Canvas) {
        val left = 14*density;val right = width-left;val y = height*.5f
        val x = left+(right-left)*(value-min).toFloat()/(max-min)
        paint.strokeCap = Paint.Cap.ROUND;paint.strokeWidth = 4*density
        paint.color = Color.parseColor(if(Appearance.dark(context)) "#35404E" else "#E8EDF4");canvas.drawLine(left,y,right,y,paint)
        paint.color = Appearance.background(context,Color.parseColor("#007AFF"));canvas.drawLine(left,y,x,y,paint)
        paint.color = Appearance.background(context,Color.parseColor("#18007AFF"));canvas.drawCircle(x,y,12*density,paint)
        paint.color = Color.WHITE;canvas.drawCircle(x,y,9*density,paint)
        paint.color = Appearance.background(context,Color.parseColor("#007AFF"));canvas.drawCircle(x,y,4*density,paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val left = 14*density;val span = (width-2*left).coerceAtLeast(1f)
                edit((min+(max-min)*((event.x-left)/span).coerceIn(0f,1f)).roundToInt());return true
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false);performClick();return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false);return true }
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick();return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { edit(value-step);true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { edit(value+step);true }
        else -> super.onKeyDown(keyCode,event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min.toFloat(),max.toFloat(),value.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val progress = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                edit(progress.roundToInt());true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { edit(value+step);true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { edit(value-step);true }
            else -> super.performAccessibilityAction(action,arguments)
        }
    }
}

/** Illustrative framing preview, clearly labelled separately from the live maps. */
private class CameraPreview(context: android.content.Context) : View(context) {
    var config = CameraConfig();set(value) { field = value;invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat();val h = height.toFloat()
        paint.color = Appearance.background(context,Color.parseColor("#F4F7FB"));canvas.drawRoundRect(0f,12f,w,h-12,20f,20f,paint)
        val perspective = sin(Math.toRadians(config.tilt.toDouble())).toFloat()
        val scale = (100f/config.distancePercent).toDouble().powHalf().toFloat()
        fun project(x: Float,z: Float): Pair<Float,Float> {
            val factor = 1/(1+z*(.15f+perspective*.65f))
            return w*.5f+x*w*.2f*scale*factor to h*.72f-z*h*.16f*scale*factor*(1-perspective*.35f)
        }
        paint.strokeCap = Paint.Cap.ROUND
        for (road in -2..2) {
            val a = project(road.toFloat(),-.3f);val b = project(road.toFloat(),8f)
            paint.color = Appearance.background(context,Color.parseColor("#E0E7EF"));paint.strokeWidth = 12f;canvas.drawLine(a.first,a.second,b.first,b.second,paint)
        }
        for (cross in 1..8) {
            val a = project(-2.5f,cross.toFloat());val b = project(2.5f,cross.toFloat())
            paint.color = if(Appearance.dark(context)) Color.parseColor("#35404E") else Color.WHITE;paint.strokeWidth = 7f;canvas.drawLine(a.first,a.second,b.first,b.second,paint)
        }
        val a = project(0f,0f);val b = project(0f,8f)
        if(!Appearance.dark(context)) { paint.color = Color.WHITE;paint.strokeWidth = 17f;canvas.drawLine(a.first,a.second,b.first,b.second,paint) }
        paint.color = Appearance.background(context,Color.parseColor("#2364F5"));paint.strokeWidth = 11f;canvas.drawLine(a.first,a.second,b.first,b.second,paint)
        val size = 16*resources.displayMetrics.density
        val arrow = Path().apply { moveTo(a.first,a.second-size);lineTo(a.first+size*.65f,a.second+size*.4f);lineTo(a.first,a.second+size*.1f);lineTo(a.first-size*.65f,a.second+size*.4f);close() }
        paint.style = Paint.Style.FILL;paint.color = Appearance.background(context,Color.parseColor("#007AFF"));canvas.drawPath(arrow,paint)
    }
    private fun Double.powHalf() = kotlin.math.sqrt(this)
}
