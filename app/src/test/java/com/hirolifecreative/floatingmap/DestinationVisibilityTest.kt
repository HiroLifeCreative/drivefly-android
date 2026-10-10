package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DestinationVisibilityTest {
    @Test fun destinationIsHiddenFarAwayAndFullyVisibleNearArrival() {
        assertEquals(0f,DestinationVisibility.alpha(2500.0),0f)
        assertEquals(0f,DestinationVisibility.alpha(1609.344),0f)
        assertEquals(1f,DestinationVisibility.alpha(500.0),0f)
        assertEquals(1f,DestinationVisibility.alpha(2.0),0f)
    }
    @Test fun approachFadesInWithoutAnAbruptDistanceThreshold() {
        val distances=listOf(1609.344,1500.0,1200.0,800.0,500.0)
        val values=distances.map { DestinationVisibility.alpha(it) }
        assertTrue(values.zipWithNext().all { (a,b)-> b>a })
        assertTrue(DestinationVisibility.alpha(1609.0)<.001f)
        assertTrue(DestinationVisibility.alpha(500.1)>.999f)
    }
    @Test fun missingOrInvalidLocationDoesNotShowTheDestination() {
        for (d in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-1.0))
            assertEquals(0f,DestinationVisibility.alpha(d),0f)
    }
}
