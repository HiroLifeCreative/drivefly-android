package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class ManeuverProgressTest {
    private val point=RouteParser.Coordinate(0.0,0.0)
    private val steps=listOf(RouteParser.Step("Salida","",100.0,point),RouteParser.Step("Derecha","",200.0,point),RouteParser.Step("Izquierda","",50.0,point),RouteParser.Step("Llegada","",0.0,point))
    @Test fun turnRemainsVisibleBeforeAndDuringIntersection() {
        assertEquals(1,ManeuverProgress.next(steps,95.0))
        assertEquals(1,ManeuverProgress.next(steps,105.0))
        assertEquals(2,ManeuverProgress.next(steps,112.0))
    }
    @Test fun laterTurnsFollowTheirOwnBoundary() {
        assertEquals(2,ManeuverProgress.next(steps,300.0))
        assertEquals(3,ManeuverProgress.next(steps,312.0))
    }
    @Test fun missingStepsAndArrivalAreSafe() {
        assertEquals(0,ManeuverProgress.next(emptyList(),100.0))
        assertEquals(3,ManeuverProgress.next(steps,900.0))
    }
}
