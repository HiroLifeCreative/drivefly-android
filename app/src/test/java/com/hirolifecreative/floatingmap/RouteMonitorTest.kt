package com.hirolifecreative.floatingmap

import org.junit.Assert.*
import org.junit.Test

class RouteMonitorTest {
    private val road = listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.01,0.0))
    @Test fun midpointOfLongSegmentIsOnRoadNotFarFromVertices() {
        val match = RouteMonitor.match(road,.005,0.0)!!
        assertEquals(0.0,match.distance,.01)
        assertTrue(match.along > 500)
        assertTrue(match.total > 1000)
    }
    @Test fun sustainedOffRoadRecalculatesAfterThreeFixes() {
        val detector = RouteMonitor()
        assertFalse(detector.evaluate(road,.005,.001,5.0,8.0,90.0).second)
        assertFalse(detector.evaluate(road,.005,.001,5.0,8.0,90.0).second)
        assertTrue(detector.evaluate(road,.005,.001,5.0,8.0,90.0).second)
    }
    @Test fun reverseOnSameRoadRecalculatesDespiteZeroLateralDistance() {
        val detector = RouteMonitor()
        detector.evaluate(road,.005,0.0,5.0,8.0,90.0)
        assertFalse(detector.evaluate(road,.0047,0.0,5.0,8.0,270.0).second)
        assertFalse(detector.evaluate(road,.0044,0.0,5.0,8.0,270.0).second)
        assertTrue(detector.evaluate(road,.0041,0.0,5.0,8.0,270.0).second)
    }
    @Test fun poorAccuracyAndStationaryHeadingDoNotTriggerWrongWay() {
        val detector = RouteMonitor()
        repeat(6) { assertFalse(detector.evaluate(road,.005,.002,100.0,8.0,270.0).second) }
        detector.evaluate(road,.005,0.0,5.0,8.0,90.0)
        repeat(6) { assertFalse(detector.evaluate(road,.004,0.0,5.0,0.0,270.0).second) }
    }
    @Test fun recoveryAndNewRouteResetDeviationCounter() {
        val detector = RouteMonitor()
        detector.evaluate(road,.005,.001,5.0,8.0,90.0)
        detector.evaluate(road,.005,0.0,5.0,8.0,90.0)
        assertFalse(detector.evaluate(road,.005,.001,5.0,8.0,90.0).second)
        detector.reset()
        assertEquals(0.0,detector.maxProgress,0.0)
    }
    @Test fun reverseBeyondOriginIsDetected() {
        val detector = RouteMonitor()
        detector.evaluate(road,0.0,0.0,5.0,8.0,90.0)
        assertFalse(detector.evaluate(road,-.0002,0.0,5.0,8.0,270.0).second)
        assertFalse(detector.evaluate(road,-.0003,0.0,5.0,8.0,270.0).second)
        assertTrue(detector.evaluate(road,-.0004,0.0,5.0,8.0,270.0).second)
    }
    @Test fun deviationDoesNotConsumeUntravelledRoute() {
        val detector=RouteMonitor()
        detector.evaluate(road,.002,0.0,5.0,8.0,90.0)
        val before=detector.maxProgress
        detector.evaluate(road,.009,.001,5.0,8.0,90.0)
        assertEquals(before,detector.maxProgress,0.0)
    }
    @Test fun highFrequencyUpdatesRequireFourSecondsOfDeviation() {
        val detector=RouteMonitor()
        for (time in listOf(1000L,1100L,1200L,3000L)) {
            assertFalse(detector.evaluate(road,.005,.001,5.0,8.0,90.0,time).second)
        }
        assertTrue(detector.evaluate(road,.005,.001,5.0,8.0,90.0,5000L).second)
    }
    @Test fun inaccurateFixBreaksSustainedDeviation() {
        val detector=RouteMonitor()
        detector.evaluate(road,.005,.001,5.0,8.0,90.0,1000)
        detector.evaluate(road,.005,.001,100.0,8.0,90.0,5000)
        assertFalse(detector.evaluate(road,.005,.001,5.0,8.0,90.0,6000).second)
    }
}
