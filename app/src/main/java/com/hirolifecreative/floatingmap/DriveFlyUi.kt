package com.hirolifecreative.floatingmap

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.widget.*

/** Shared visual language for native DriveFly screens. */
object DriveFlyUi {
    const val BACKGROUND = "#F2F2F7"
    const val TEXT = "#1C1C1E"
    const val MUTED = "#636366"
    const val BLUE = "#007AFF"
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun shape(c: Context, color: String, radius: Int = 18) = GradientDrawable().apply {
        setColor(Appearance.background(c,Color.parseColor(color))); cornerRadius = dp(c, radius).toFloat()
    }
    fun text(c: Context, value: String, size: Float = 16f, bold: Boolean = false, color: String = TEXT) = TextView(c).apply {
        text = value; textSize = size; setTextColor(Appearance.text(c,Color.parseColor(color)))
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    fun button(c: Context, label: String, primary: Boolean = false, action: () -> Unit) = Button(c).apply {
        text = label; textSize = 15f; isAllCaps = false; stateListAnimator = null
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        setTextColor(Appearance.text(c,Color.parseColor(if (primary) "#FFFFFF" else TEXT)))
        background = RippleDrawable(ColorStateList.valueOf(0x18007AFF), shape(c, if (primary) BLUE else "#F2F3F5", 24), null)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(c, 14), 0, dp(c, 14), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 48)).apply { topMargin = dp(c, 10) }
        setOnClickListener { action() }
    }
    fun icon(c: Context, name: String, color: String = TEXT, size: Int = 24) = ImageView(c).apply {
        setImageDrawable(DriveFlyIcon(name, Appearance.text(c,Color.parseColor(color))))
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size))
    }
    fun iconButton(c: Context, name: String, label: String, action: () -> Unit) = ImageButton(c).apply {
        setImageDrawable(DriveFlyIcon(name, Appearance.text(c,Color.parseColor(TEXT))))
        contentDescription = label
        background = RippleDrawable(ColorStateList.valueOf(0x18000000), shape(c, "#F2F3F5", 22), null)
        setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
        layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
        setOnClickListener { action() }
    }
    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; background = shape(c, "#FFFFFF", 24)
        setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 16) }
    }
    fun chrome(a: Activity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(a.window, true)
        a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        a.window.decorView.post {
            androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
                isAppearanceLightStatusBars = !Appearance.dark(a)
                isAppearanceLightNavigationBars = !Appearance.dark(a)
            }
        }
        a.window.statusBarColor = Appearance.background(a,Color.parseColor(BACKGROUND))
        a.window.navigationBarColor = Appearance.background(a,Color.parseColor(BACKGROUND))
        androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
            isAppearanceLightStatusBars = !Appearance.dark(a)
            isAppearanceLightNavigationBars = !Appearance.dark(a)
        }
    }
}
