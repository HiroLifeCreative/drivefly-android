package com.hirolifecreative.floatingmap

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*

/** Compact grouped settings inspired by the selected Arif Hossain reference. No Maps SDK. */
object SettingsUi {
    fun dp(c: Context,n: Int)=(n*c.resources.displayMetrics.density).toInt()
    fun background(c: Context)=Color.parseColor(if(Appearance.dark(c)) "#090D12" else "#F4F5F7")
    fun surface(c: Context)=Color.parseColor(if(Appearance.dark(c)) "#141B24" else "#FFFFFF")
    fun ink(c: Context)=Color.parseColor(if(Appearance.dark(c)) "#F4F6FA" else "#19212C")
    fun muted(c: Context)=Color.parseColor(if(Appearance.dark(c)) "#98A6B8" else "#697586")
    fun accent(c: Context)=Color.parseColor(if(Appearance.dark(c)) "#73ACFF" else "#2364F5")
    fun shape(c: Context,color: Int=surface(c),radius: Int=14)=GradientDrawable().apply { setColor(color);cornerRadius=dp(c,radius).toFloat() }
    fun text(c: Context,value: String,size: Float=15f,bold: Boolean=false,secondary: Boolean=false)=TextView(c).apply {
        text=value;textSize=size;includeFontPadding=false;setTextColor(if(secondary) muted(c) else ink(c))
        typeface=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        gravity=Gravity.START or Gravity.CENTER_VERTICAL
    }
    fun chrome(a: Activity) { DriveFlyUi.chrome(a);a.window.statusBarColor=background(a);a.window.navigationBarColor=background(a) }
    class Page(val activity: Activity,title: String,save: (() -> Unit)?=null) {
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(activity,16),dp(activity,8),dp(activity,16),dp(activity,28)) }
        init {
            val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(background(activity)) }
            val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(activity,12),dp(activity,6),dp(activity,16),dp(activity,6));minimumHeight=dp(activity,64) }
            header.addView(android.widget.ImageButton(activity).apply {
                setImageDrawable(DriveFlyIcon("back",ink(activity)));contentDescription="Volver"
                setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12))
                background=RippleDrawable(ColorStateList.valueOf(0x20808080),shape(activity),null)
                layoutParams=LinearLayout.LayoutParams(dp(activity,44),dp(activity,44));setOnClickListener { activity.finish() }
            })
            header.addView(text(activity,title,20f,true).apply { setPadding(dp(activity,14),0,dp(activity,6),0) },LinearLayout.LayoutParams(0,-2,1f))
            if(save!=null) header.addView(Button(activity).apply {
                text="Guardar";isAllCaps=false;textSize=14f;includeFontPadding=false;setTextColor(accent(activity))
                minWidth=0;minimumWidth=0;setPadding(dp(activity,8),0,dp(activity,8),0)
                background=RippleDrawable(ColorStateList.valueOf(0x20808080),shape(activity,SettingsUi.background(activity),10),null)
                setOnClickListener { save() }
            },LinearLayout.LayoutParams(-2,dp(activity,48)))
            root.addView(header)
            root.addView(ScrollView(activity).apply { isFillViewport=true;addView(content) },LinearLayout.LayoutParams(-1,0,1f))
            activity.setContentView(root);chrome(activity)
        }
        fun section(title: String): LinearLayout {
            content.addView(text(activity,title,14f,true).apply { setPadding(dp(activity,4),dp(activity,20),0,dp(activity,10)) })
            return group(activity).also { content.addView(it) }
        }
        fun note(value: String): TextView = text(activity,value,12f,secondary=true).apply {
            setPadding(dp(activity,4),dp(activity,9),dp(activity,4),dp(activity,2));content.addView(this)
        }
    }
    fun group(c: Context)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL;background=shape(c);clipToOutline=true }
    private fun divider(group: LinearLayout) {
        if(group.childCount==0) return
        val c=group.context
        group.addView(View(c).apply { setBackgroundColor(Color.parseColor(if(Appearance.dark(c)) "#26303D" else "#E9EDF2")) },LinearLayout.LayoutParams(-1,1).apply { leftMargin=dp(c,50);rightMargin=dp(c,14) })
    }
    fun row(group: LinearLayout,title: String,icon: String="gear",value: String="",description: String="",trailing: View?=null,selected: Boolean=false,action: (() -> Unit)?=null): LinearLayout {
        divider(group);val c=group.context
        val row=LinearLayout(c).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(c,56);setPadding(dp(c,14),dp(c,11),dp(c,14),dp(c,11)) }
        row.addView(ImageView(c).apply { setImageDrawable(DriveFlyIcon(icon,if(selected) accent(c) else ink(c))) },LinearLayout.LayoutParams(dp(c,22),dp(c,22)).apply { rightMargin=dp(c,13) })
        row.addView(LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL
            addView(text(c,title).apply { if(selected) setTextColor(accent(c)) })
            if(description.isNotBlank()) addView(text(c,description,12f,secondary=true).apply { setPadding(0,dp(c,4),dp(c,6),0) })
        },LinearLayout.LayoutParams(0,-2,1f))
        if(value.isNotBlank()) row.addView(text(c,value,13f,secondary=true).apply { maxWidth=dp(c,105);maxLines=1;ellipsize=TextUtils.TruncateAt.END;setPadding(dp(c,6),0,0,0) })
        if(trailing!=null) row.addView(trailing)
        else if(selected) row.addView(text(c,"✓",18f,true).apply { setTextColor(accent(c));setPadding(dp(c,12),0,0,0) })
        else if(action!=null) row.addView(ImageView(c).apply { setImageDrawable(DriveFlyIcon("chevron",muted(c))) },LinearLayout.LayoutParams(dp(c,16),dp(c,18)).apply { leftMargin=dp(c,8) })
        if(action!=null) {
            row.isFocusable=true;row.isClickable=true
            row.foreground=RippleDrawable(ColorStateList.valueOf(0x20808080),null,null)
            row.setOnClickListener { action() }
        }
        group.addView(row);return row
    }
    fun toggle(group: LinearLayout,title: String,icon: String="gear",checked: Boolean,description: String="",change: (Boolean)->Unit): DriveFlySwitch {
        val toggle=DriveFlySwitch(group.context).apply { contentDescription=title;isChecked=checked;setOnCheckedChangeListener { _,v -> change(v) } }
        row(group,title,icon,description=description,trailing=toggle,action={ toggle.isChecked=!toggle.isChecked })
        return toggle
    }
    fun body(group: LinearLayout): LinearLayout {
        divider(group);val c=group.context
        return LinearLayout(c).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));group.addView(this) }
    }
    fun input(c: Context,hint: String)=EditText(c).apply {
        this.hint=hint;textSize=14f;setTextColor(ink(c));setHintTextColor(muted(c));isSingleLine=true
        background=shape(c,if(Appearance.dark(c)) Color.parseColor("#202A37") else Color.parseColor("#F1F4F8"),10)
        setPadding(dp(c,12),dp(c,13),dp(c,12),dp(c,13))
    }
}
