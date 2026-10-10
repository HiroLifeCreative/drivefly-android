package com.hirolifecreative.floatingmap

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Business details expand inside the map sheet, without another dimmed dialog. */
class BusinessPanel(context: Context) : LinearLayout(context) {
    private val badge = DriveFlyUi.text(context,"Consultando horario…",12f,true)
    private val todayValue = DriveFlyUi.text(context,"Ver semana",13f,true)
    private val week = LinearLayout(context).apply { orientation = VERTICAL }
    private val weekScroll = ScrollView(context)
    private var lastDetails: PlacesClient.Details? = null
    private var lastToday: Int? = null
    init {
        orientation = VERTICAL
        setPadding(0,0,0,DriveFlyUi.dp(context,8))
        badge.background = DriveFlyUi.shape(context,"#F2F4F7",12)
        badge.setPadding(DriveFlyUi.dp(context,10),DriveFlyUi.dp(context,7),DriveFlyUi.dp(context,10),DriveFlyUi.dp(context,7))
        badge.layoutParams = LayoutParams(-2,-2).apply { bottomMargin = DriveFlyUi.dp(context,8) }
        addView(badge)
        val hoursRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = DriveFlyUi.dp(context,38)
            addView(DriveFlyUi.text(context,"Horario de hoy",13f,color=DriveFlyUi.MUTED).apply { layoutParams = LayoutParams(0,-2,1f) })
            addView(todayValue.apply { setPadding(DriveFlyUi.dp(context,8),0,DriveFlyUi.dp(context,8),0) })
            addView(DriveFlyUi.icon(context,"chevron",DriveFlyUi.MUTED,14))
            setOnClickListener {
                if (lastDetails?.hours != null && !lastDetails!!.permanentlyClosed) weekScroll.visibility = if (weekScroll.visibility == VISIBLE) GONE else VISIBLE
            }
            contentDescription = "Expandir o cerrar los horarios de la semana"
        }
        addView(hoursRow)
        weekScroll.apply { addView(week);visibility = GONE;layoutParams = LayoutParams(-1,DriveFlyUi.dp(context,172)) }
        addView(weekScroll)
        addView(DriveFlyUi.text(context,"Según horario publicado · puede variar en festivos",10f,color=DriveFlyUi.MUTED).apply { setPadding(0,DriveFlyUi.dp(context,5),0,0) })
    }
    fun update(details: PlacesClient.Details?,loading: Boolean = false) {
        val status = BusinessHours.status(details?.hours,details?.timezone,details?.permanentlyClosed ?: false)
        badge.text = if (details == null && loading) "Consultando horario…" else status.label
        val tint = when(status.state) {
            BusinessHours.State.OPEN -> "#E8F7EF" to "#148454"
            BusinessHours.State.CLOSING -> "#FFF3D6" to "#97680A"
            BusinessHours.State.CLOSED -> "#EDF0F4" to "#627080"
            BusinessHours.State.UNKNOWN -> "#F2F4F7" to "#657283"
        }
        badge.background = DriveFlyUi.shape(context,tint.first,12);badge.setTextColor(Color.parseColor(tint.second))
        val today = BusinessHours.todayIndex(details?.timezone)
        val rows = if (details?.permanentlyClosed == true) null else BusinessHours.weeklyRows(details?.hours)
        todayValue.text = when {
            details?.permanentlyClosed == true -> "Sin actividad"
            rows != null && today != null -> rows[today]
            rows != null -> "Ver semana"
            details?.hours != null -> "Ver horario"
            else -> "No disponible"
        }
        todayValue.maxLines = 1;todayValue.ellipsize = android.text.TextUtils.TruncateAt.END
        todayValue.layoutParams = LayoutParams(-2,-2).apply { width = DriveFlyUi.dp(context,156) }
        todayValue.gravity = Gravity.END
        if (details == lastDetails && today == lastToday) return
        lastDetails = details;lastToday = today;week.removeAllViews()
        if (rows != null) rows.forEachIndexed { index,hours ->
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL;setPadding(DriveFlyUi.dp(context,10),DriveFlyUi.dp(context,8),DriveFlyUi.dp(context,10),DriveFlyUi.dp(context,8))
                if (index == today) background = DriveFlyUi.shape(context,"#F2F5F9",10)
                addView(DriveFlyUi.text(context,BusinessHours.dayNames[index],12f,index == today).apply { layoutParams = LayoutParams(0,-2,1f) })
                addView(DriveFlyUi.text(context,hours,12f,index == today,color=if (hours == "Cerrado") DriveFlyUi.MUTED else DriveFlyUi.TEXT).apply { gravity = Gravity.END })
            };week.addView(row)
        } else if (details?.hours != null && !details.permanentlyClosed) {
            week.addView(DriveFlyUi.text(context,details.hours.replace(';','\n'),12f,color=DriveFlyUi.MUTED))
        }
    }
}
