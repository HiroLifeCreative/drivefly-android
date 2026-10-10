package com.hirolifecreative.floatingmap
import android.content.Context
import android.graphics.*
import android.view.View

/** Lightweight preview that works without starting the map renderer. */
class NavigationColorPreview(context: Context,private val route: Boolean,var color: String): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    override fun onDraw(canvas: Canvas) {
        val d=resources.displayMetrics.density
        paint.style=Paint.Style.FILL;paint.color=SettingsUi.surface(context)
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),14*d,14*d,paint)
        val tint=Color.parseColor(color)
        if(route) {
            path.reset();path.moveTo(width*.12f,height*.7f);path.lineTo(width*.45f,height*.7f);path.lineTo(width*.45f,height*.3f);path.lineTo(width*.88f,height*.3f)
            paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            paint.color=Color.parseColor(NavigationColorConfig.casing(color));paint.strokeWidth=14*d;canvas.drawPath(path,paint)
            paint.color=tint;paint.strokeWidth=10*d;canvas.drawPath(path,paint)
        } else {
            canvas.save();canvas.translate(width/2f,height/2f-3*d);canvas.scale(d,d)
            path.reset();path.moveTo(0f,32f);path.cubicTo(-7f,19f,-21f,9f,-21f,-7f);path.cubicTo(-21f,-34f,21f,-34f,21f,-7f);path.cubicTo(21f,9f,7f,19f,0f,32f);path.close()
            paint.style=Paint.Style.FILL;paint.color=Color.parseColor(NavigationColorConfig.casing(color));canvas.drawPath(path,paint)
            canvas.save();canvas.scale(.9f,.9f);paint.color=tint;canvas.drawPath(path,paint);canvas.restore()
            paint.color=Color.WHITE;canvas.drawCircle(0f,-9f,8f,paint);canvas.restore()
        }
    }
}
