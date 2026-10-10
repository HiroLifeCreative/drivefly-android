package com.hirolifecreative.floatingmap

import org.junit.Assert.*
import org.junit.Test

class RouteParserTest {
    @Test fun readsFullGeometryAndDistinctManeuverLocations() {
        val route = RouteParser.parse("""{
          "code":"Ok","routes":[{
            "legs":[{"steps":[
              {"distance":300,"name":"Main","maneuver":{"location":[-99.1,19.4],"type":"depart"}},
              {"name":"Oak","maneuver":{"modifier":"right","type":"turn","location":[-99.2,19.5]},"distance":100},
              {"maneuver":{"type":"arrive","location":[-99.3,19.6]},"distance":0}
            ]}],
            "geometry":{"coordinates":[[-99.1,19.4],[-99.15,19.45],[-99.2,19.5],[-99.3,19.6]]}
          }]
        }""")
        assertEquals(4, route.coordinates.size)
        assertEquals(3, route.steps.size)
        assertEquals("Gira a la derecha", route.steps[1].instruction)
        assertEquals(-99.2, route.steps[1].location.longitude, 0.00001)
        assertNotEquals(route.steps[0].location, route.steps[2].location)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNoRouteResponse() { RouteParser.parse("""{"code":"NoRoute","routes":[]}""") }
}
