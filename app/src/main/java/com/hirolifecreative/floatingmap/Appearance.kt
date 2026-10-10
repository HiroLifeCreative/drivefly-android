package com.hirolifecreative.floatingmap

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/** Platform-only: safe to use in the recovery/settings process. */
object Appearance {
    const val ACTION = "com.hirolifecreative.floatingmap.APPEARANCE"
    private var stamp = Long.MIN_VALUE
    private var saved = "system"
    fun mode(c: Context): String {
        val f = File(c.filesDir,"appearance.txt")
        if (f.lastModified()!=stamp) {
            saved = try { AtomicFile(f).openRead().bufferedReader().use { it.readText() }.trim().takeIf { it in listOf("light","dark","system") } ?: "system" } catch (_: Exception) { "system" }
            stamp = f.lastModified()
        }
        return saved
    }
    fun dark(c: Context) = mode(c)=="dark" || (mode(c)=="system" && c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    fun save(c: Context,mode: String) {
        require(mode in listOf("light","dark","system"))
        val file = AtomicFile(File(c.filesDir,"appearance.txt"));val out = file.startWrite()
        try { out.write(mode.toByteArray());file.finishWrite(out) } catch (e: Exception) { file.failWrite(out);throw e }
        stamp = Long.MIN_VALUE
        c.sendBroadcast(Intent(ACTION).setPackage(c.packageName))
    }
    fun background(c: Context,value: Int) = ThemePalette.background(value,dark(c))
    fun text(c: Context,value: Int) = ThemePalette.text(value,dark(c))
    fun applyTree(view: View) {
        when (val bg = view.background) {
            is GradientDrawable -> bg.color?.defaultColor?.let { bg.setColor(background(view.context,it)) }
            is ColorDrawable -> bg.color = background(view.context,bg.color)
        }
        if (view is TextView) { view.setTextColor(text(view.context,view.currentTextColor));view.setHintTextColor(text(view.context,view.currentHintTextColor)) }
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) applyTree(view.getChildAt(i))
    }
}
