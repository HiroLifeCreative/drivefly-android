package com.hirolifecreative.floatingmap

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView

/** Custom draggable surface: drag the handle, tap it to expand/collapse. */
class DriveFlySheet(context: Context) : LinearLayout(context) {
    var onOffset: ((Int) -> Unit)? = null
    private var downY = 0f
    private var downOffset = 0f
    private var moved = false
    init { orientation = VERTICAL }
    fun handle(): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(-1, DriveFlyUi.dp(context, 28))
        addView(View(context).apply {
            background = DriveFlyUi.shape(context, "#CDD1D9", 3)
            layoutParams = LayoutParams(DriveFlyUi.dp(context, 38), DriveFlyUi.dp(context, 5))
        })
        contentDescription = "Deslizar o tocar para expandir la tarjeta"
        setOnClickListener { snap(if (this@DriveFlySheet.translationY > 0) 0f else collapsed()) }
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { this@DriveFlySheet.animate().cancel(); downY = event.rawY; downOffset = this@DriveFlySheet.translationY; moved = false; parent.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - downY
                    if (kotlin.math.abs(delta) > DriveFlyUi.dp(context, 6)) moved = true
                    this@DriveFlySheet.translationY = (downOffset + delta).coerceIn(0f, collapsed()); notifyOffset()
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) v.performClick() else snap(if (this@DriveFlySheet.translationY > collapsed() / 2) collapsed() else 0f)
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> snap(if (this@DriveFlySheet.translationY > collapsed() / 2) collapsed() else 0f)
            }; true
        }
    }
    private fun collapsed() = (height - DriveFlyUi.dp(context, 128)).coerceAtLeast(0).toFloat()
    private fun notifyOffset() { onOffset?.invoke((height - translationY).toInt()) }
    private fun snap(offset: Float) {
        animate().translationY(offset).setDuration(220).setUpdateListener { notifyOffset() }.start()
    }
    companion object {
        fun choices(activity: Activity, title: String, message: String? = null, actions: List<Pair<String, () -> Unit>>) {
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val body = LinearLayout(activity).apply {
                orientation = VERTICAL; background = DriveFlyUi.shape(activity, "#FFFFFF", 28)
                setPadding(DriveFlyUi.dp(activity, 24), DriveFlyUi.dp(activity, 16), DriveFlyUi.dp(activity, 24), DriveFlyUi.dp(activity, 28))
                addView(View(activity).apply {
                    background = DriveFlyUi.shape(activity, "#CDD1D9", 3)
                    layoutParams = LayoutParams(DriveFlyUi.dp(activity, 38), DriveFlyUi.dp(activity, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = DriveFlyUi.dp(activity, 20) }
                })
                addView(DriveFlyUi.text(activity, title, 23f, true))
                if (message != null) addView(DriveFlyUi.text(activity, message, 14f, color = DriveFlyUi.MUTED).apply { setPadding(0,DriveFlyUi.dp(activity,12),0,DriveFlyUi.dp(activity,12)) })
                actions.forEachIndexed { index, (label, action) ->
                    addView(DriveFlyUi.button(activity, label, index == 0) { dialog.dismiss(); action() })
                }
                addView(DriveFlyUi.button(activity, "Cerrar") { dialog.dismiss() })
            }
            dialog.setContentView(ScrollView(activity).apply { addView(body); background = ColorDrawable(Color.TRANSPARENT) })
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = .25f }
                setGravity(Gravity.BOTTOM); setLayout(-1,-2)
            }
            dialog.show()
            dialog.window?.setLayout(-1, -2)
            body.translationY = DriveFlyUi.dp(activity, 60).toFloat(); body.alpha = 0f
            body.animate().translationY(0f).alpha(1f).setDuration(240).start()
        }
    }
}

/** Drawn from scratch; keeps checked-state accessibility without Android switch styling. */
class DriveFlySwitch(context: Context) : CompoundButton(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        buttonDrawable = null; background = null
        layoutParams = LinearLayout.LayoutParams(DriveFlyUi.dp(context,52),DriveFlyUi.dp(context,44))
        setPadding(0,0,0,0)
    }
    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val left = (width - 48*d)/2; val top = (height - 28*d)/2
        paint.color = Color.parseColor(if (isChecked) "#2364F5" else if(Appearance.dark(context)) "#35404E" else "#DFE4EE")
        canvas.drawRoundRect(left,top,left+48*d,top+28*d,14*d,14*d,paint)
        paint.color = Color.WHITE
        canvas.drawCircle(left + (if (isChecked) 34 else 14)*d,top+14*d,11*d,paint)
    }
}
