package com.hirolifecreative.floatingmap

import org.junit.Assert.*
import org.junit.Test

class RoutingClientTest {
    private val response = """{"code":"Ok","routes":[{"duration":125.5,"distance":900.2,"geometry":{"coordinates":[[-81.4,29.8],[-81.39,29.81]]},"legs":[{"steps":[{"distance":900.2,"name":"Main","maneuver":{"type":"depart","instruction":"Conduce hacia el norte","location":[-81.4,29.8]}},{"distance":0,"maneuver":{"type":"arrive","location":[-81.39,29.81]}}]}]}]}"""
    @Test fun mapboxResponsePreservesInstructionsAndPreviewMetrics() {
        val route = RoutingClient.decode(response)
        assertEquals(125.5,route.duration,0.001)
        assertEquals(900.2,route.distance,0.001)
        assertEquals("Conduce hacia el norte",route.route.steps.first().instruction)
        assertEquals(2,route.route.coordinates.size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun noRoadConnectionIsNotPresentedAsValidRoute() { RoutingClient.decode("""{"code":"NoRoute","routes":[]}""") }
    @Test(expected = org.json.JSONException::class)
    fun missingPreviewMetricsRejectsIncompleteResponse() { RoutingClient.decode(response.replace("\"duration\":125.5,", "")) }
    @Test fun alternativesKeepTheirOwnGeometryAndMetrics() {
        val root = org.json.JSONObject(response)
        val alternative = org.json.JSONObject(root.getJSONArray("routes").getJSONObject(0).toString())
        alternative.put("duration",200.0)
        alternative.getJSONObject("geometry").put("coordinates",org.json.JSONArray("[[-81.4,29.8],[-81.38,29.82],[-81.39,29.81]]"))
        root.getJSONArray("routes").put(alternative)
        val route = RoutingClient.decode(root.toString())
        assertEquals(1,route.alternatives.size)
        assertEquals(200.0,route.alternatives.first().duration,0.001)
        assertEquals(3,route.alternatives.first().route.coordinates.size)
    }
    @Test fun cachedRoutesPreserveTrafficAvailability() {
        val json = org.json.JSONObject(response).put("drivefly_traffic",true).toString()
        val traffic = RoutingClient.decode(json)
        assertTrue(traffic.trafficAware)
        assertTrue(RoutingClient.decode(traffic.json).trafficAware)
        assertFalse(RoutingClient.decode(response).trafficAware)
    }
}
