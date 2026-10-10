package com.hirolifecreative.floatingmap

import org.json.JSONArray

/** Nominatim returns JSON objects with no guaranteed field order. */
object SearchParser {
    data class Result(val name: String, val address: String, val latitude: Double, val longitude: Double)
    fun parse(json: String): List<Result> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val address = item.optString("display_name").trim()
            if (address.isEmpty()) return@mapNotNull null
            Result(address.split(",").take(2).joinToString(",").trim(), address, lat, lon)
        }
    }
}
