package com.hirolifecreative.floatingmap

import org.json.JSONObject

/** Reads OSRM route geometry and maneuver locations independently of JSON field order. */
object RouteParser {
    data class Coordinate(val longitude: Double, val latitude: Double)
    data class Step(val instruction: String, val road: String, val distance: Double, val location: Coordinate,val lanes: List<LaneGuidance.Lane> = emptyList())
    data class Route(val coordinates: List<Coordinate>, val steps: List<Step>)

    fun parse(json: String): Route {
        val body = JSONObject(json)
        require(body.optString("code") == "Ok") { "No hay una ruta disponible para ese destino" }
        val route = body.getJSONArray("routes").getJSONObject(0)
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coordinates.length()).map { i ->
            val coordinate = coordinates.getJSONArray(i)
            Coordinate(coordinate.getDouble(0), coordinate.getDouble(1))
        }
        val steps = mutableListOf<Step>()
        val legs = route.getJSONArray("legs")
        for (i in 0 until legs.length()) {
            val array = legs.getJSONObject(i).getJSONArray("steps")
            for (j in 0 until array.length()) {
                val step = array.getJSONObject(j)
                val maneuver = step.getJSONObject("maneuver")
                val type = maneuver.optString("type")
                val modifier = maneuver.optString("modifier")
                val instruction = maneuver.optString("instruction").ifBlank { when {
                    type == "arrive" -> "Continúa hasta tu destino"
                    type == "depart" -> "Inicia el recorrido"
                    type.contains("roundabout") || type == "rotary" -> "En la rotonda, toma la salida ${maneuver.optInt("exit", 1)}"
                    modifier.contains("uturn") -> "Da la vuelta"
                    modifier.contains("left") -> "Gira a la izquierda"
                    modifier.contains("right") -> "Gira a la derecha"
                    else -> "Sigue recto"
                } }
                val location = maneuver.getJSONArray("location")
                val coordinate=Coordinate(location.getDouble(0),location.getDouble(1))
                steps.add(Step(instruction,step.optString("name"),step.optDouble("distance"),coordinate,LaneGuidance.parse(step,coordinate)))
            }
        }
        require(points.size >= 2 && steps.isNotEmpty()) { "La ruta recibida está incompleta" }
        return Route(points, steps)
    }
}
