package com.hirolifecreative.floatingmap

import android.graphics.*
import android.graphics.drawable.Drawable

/** A consistent line icon set drawn in native pixels, without emoji fonts. */
class DriveFlyIcon(private val name: String, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg xy: Float) { val p = Path(); p.moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) p.lineTo(xy[i], xy[i+1]); canvas.drawPath(p, paint) }
        fun rect(l: Float,t: Float,r: Float,b: Float) = canvas.drawRoundRect(l,t,r,b,2f,2f,paint)
        when (name) {
            "back" -> { line(14f,5f,7f,12f,14f,19f);line(7f,12f,21f,12f) }
            "voice" -> { rect(9f,3f,15f,15f);line(5f,10f,5f,13f,7f,18f,12f,20f,17f,18f,19f,13f,19f,10f);line(12f,20f,12f,23f) }
            "moon" -> { canvas.drawArc(4f,4f,21f,21f,35f,270f,false,paint);line(19f,18f,12f,16f,8f,10f,8f,4f) }
            "bell" -> { line(5f,17f,7f,14f,7f,8f,9f,4f,15f,4f,17f,8f,17f,14f,19f,17f,5f,17f);line(10f,21f,14f,21f) }
            "key" -> { canvas.drawCircle(8f,8f,4f,paint);line(11f,11f,20f,20f,22f,18f);line(17f,17f,19f,15f) }
            "info" -> { canvas.drawCircle(12f,12f,9f,paint);line(12f,11f,12f,17f);line(12f,7f,12f,7.2f) }
            "sliders" -> { line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f);canvas.drawCircle(9f,6f,2f,paint);canvas.drawCircle(15f,12f,2f,paint);canvas.drawCircle(8f,18f,2f,paint) }
            "menu" -> { line(4f,7f,20f,7f);line(4f,12f,16f,12f);line(4f,17f,20f,17f) }
            "search" -> { canvas.drawCircle(10.5f,10.5f,6.5f,paint);line(15.5f,15.5f,21f,21f) }
            "home" -> { line(3f,10f,12f,3f,21f,10f);line(5f,9f,5f,21f,19f,21f,19f,9f);line(10f,21f,10f,14f,14f,14f,14f,21f) }
            "bag" -> { rect(3f,7f,21f,20f);rect(8f,3f,16f,7f);line(3f,12f,21f,12f) }
            "layers", "map" -> { line(3f,8f,12f,3f,21f,8f,12f,13f,3f,8f);line(3f,13f,12f,18f,21f,13f);line(3f,17f,12f,22f,21f,17f) }
            "gear" -> { canvas.drawCircle(12f,12f,7f,paint);canvas.drawCircle(12f,12f,2.5f,paint);for (i in 0..7) { val a=i*Math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) } }
            "shield" -> { line(12f,3f,20f,6f,19f,15f,12f,21f,5f,15f,4f,6f,12f,3f);line(8f,12f,11f,15f,16f,9f) }
            "arrow" -> { line(4f,20f,10f,4f,20f,4f,20f,14f);line(10f,4f,20f,4f,4f,20f) }
            "locate" -> { canvas.drawCircle(12f,12f,6f,paint);canvas.drawCircle(12f,12f,2f,paint);line(12f,2f,12f,5f);line(12f,19f,12f,22f);line(2f,12f,5f,12f);line(19f,12f,22f,12f) }
            "pin" -> {
                canvas.drawPath(Path().apply {
                    moveTo(12f,22f); cubicTo(12f,22f,4f,13f,4f,9f); cubicTo(4f,4f,8f,2f,12f,2f)
                    cubicTo(16f,2f,20f,4f,20f,9f); cubicTo(20f,13f,12f,22f,12f,22f); close()
                },paint)
                canvas.drawCircle(12f,9f,3f,paint)
            }
            "close" -> { line(6f,6f,18f,18f);line(6f,18f,18f,6f) }
            "chevron" -> line(9f,5f,16f,12f,9f,19f)
            "bookmark" -> line(6f,3f,18f,3f,18f,21f,12f,17f,6f,21f,6f,3f)
            "car" -> { rect(3f,10f,21f,18f);line(5f,10f,7f,5f,17f,5f,19f,10f);line(5f,18f,5f,21f);line(19f,18f,19f,21f);line(6f,14f,8f,14f);line(16f,14f,18f,14f) }
            else -> { canvas.drawCircle(12f,12f,9f,paint);line(9f,8f,10f,6f,14f,6f,16f,9f,12f,12f,12f,14f);canvas.drawCircle(12f,18f,0.4f,paint) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
