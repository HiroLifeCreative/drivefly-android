package com.hirolifecreative.floatingmap

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Published weekly hours only. Unknown syntax/time zone never becomes 'closed'. */
object BusinessHours {
    private val days = listOf("Mo","Tu","We","Th","Fr","Sa","Su")
    val dayNames = listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo")
    enum class State(val color: String) { OPEN("#19A76C"), CLOSING("#F2B544"), CLOSED("#8C96A3"), UNKNOWN("#FFFFFF") }
    data class Status(val state: State,val label: String,val minutesToClose: Int? = null,val closesAt: String? = null)
    data class Span(val start: Int,val end: Int)
    private fun schedule(hours: String): List<List<Span>>? {
        val week = List(7) { mutableListOf<Span>() }
        val offDays = mutableSetOf<Int>(); val workingDays = mutableSetOf<Int>()
        for (raw in hours.split(';')) {
            val rule = Regex("^(Mo|Tu|We|Th|Fr|Sa|Su)(?:-(Mo|Tu|We|Th|Fr|Sa|Su))?\\s+(off|\\d{2}:\\d{2}-\\d{2}:\\d{2}(?:,\\s*\\d{2}:\\d{2}-\\d{2}:\\d{2})*)$").matchEntire(raw.trim()) ?: return null
            val start = days.indexOf(rule.groupValues[1]);val end = days.indexOf(rule.groupValues[2]).let { if (it < 0) start else it }
            val count = (end-start+7)%7+1;val selected = (0 until count).map { (start+it)%7 }
            if (rule.groupValues[3] == "off") { offDays += selected; continue }
            workingDays += selected
            for (range in rule.groupValues[3].split(',')) {
                fun time(value: String): Int? {
                    val p = value.split(':').map { it.toInt() }
                    if (p[0] !in 0..24 || p[1] !in 0..59 || p[0] == 24 && p[1] != 0) return null
                    return p[0]*60+p[1]
                }
                val pair = range.trim().split('-');val from = time(pair[0]) ?: return null;val to = time(pair[1]) ?: return null
                if (from >= 1440 || from == to) return null
                selected.forEach { week[it] += Span(from,to) }
            }
        }
        if (offDays.any { it in workingDays }) return null
        // An off rule can override an overnight interval; avoid guessing its semantics.
        if (week.indices.any { day -> week[day].any { it.end < it.start } && (day+1)%7 in offDays }) return null
        return week
    }
    fun weeklyRows(hours: String?): List<String>? {
        if (hours == "24/7") return List(7) { "24 horas" }
        val week = schedule(hours ?: return null) ?: return null
        fun text(time: Int) = "%02d:%02d".format(time/60,time%60)
        return week.map { spans -> if (spans.isEmpty()) "Cerrado" else spans.sortedBy { it.start }.joinToString(" · ") { "${text(it.start)}–${text(it.end)}" } }
    }
    fun status(hours: String?,timezone: String?,permanentlyClosed: Boolean = false,instant: Instant = Instant.now()): Status {
        if (permanentlyClosed) return Status(State.CLOSED,"Cerrado permanentemente")
        if (hours == "24/7") return Status(State.OPEN,"Abierto 24 horas")
        val unknown = Status(State.UNKNOWN,if (hours.isNullOrBlank()) "Sin horario disponible" else "Horario publicado · estado sin confirmar")
        val zone = try { ZoneId.of(timezone ?: return unknown) } catch (_: Exception) { return unknown }
        val week = schedule(hours ?: return unknown) ?: return unknown
        val today = instant.atZone(zone).toLocalDate()
        val intervals = (-1..7).flatMap { offset ->
            val date = today.plusDays(offset.toLong())
            week[date.dayOfWeek.value-1].map { span ->
                val from = date.atStartOfDay().plusMinutes(span.start.toLong()).atZone(zone).toInstant()
                val closeDate = if (span.end < span.start) date.plusDays(1) else date
                val to = closeDate.atStartOfDay().plusMinutes(span.end.toLong()).atZone(zone).toInstant()
                from to to
            }
        }.sortedBy { it.first }
        val merged = mutableListOf<Pair<Instant,Instant>>()
        intervals.forEach { interval ->
            val last = merged.lastOrNull()
            if (last != null && interval.first <= last.second) merged[merged.lastIndex] = last.first to maxOf(last.second,interval.second)
            else merged += interval
        }
        val current = merged.firstOrNull { instant >= it.first && instant < it.second } ?: return Status(State.CLOSED,"Cerrado según horario")
        val minutes = ceil((current.second.epochSecond-instant.epochSecond)/60.0).toInt()
        val closes = current.second.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (minutes <= 30) Status(State.CLOSING,"Cierra en $minutes min",minutes,closes)
        else Status(State.OPEN,"Abierto · cierra $closes",minutes,closes)
    }
    fun openNow(hours: String,timezone: String?,instant: Instant = Instant.now()): Boolean? = when(status(hours,timezone,instant = instant).state) {
        State.OPEN,State.CLOSING -> true
        State.CLOSED -> false
        State.UNKNOWN -> null
    }
    fun todayIndex(timezone: String?): Int? { return try { Instant.now().atZone(ZoneId.of(timezone ?: return null)).dayOfWeek.value-1 } catch (_: Exception) { null } }
}
