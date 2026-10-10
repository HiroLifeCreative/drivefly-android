package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class ManeuverNoticeTest {
    @Test fun distantVisibleIntersectionDoesNotTriggerNotice() {
        assertFalse(ManeuverNotice.visible(16093.0,0.0,0.0,5.0,0))
        assertFalse(ManeuverNotice.visible(100.0,94.0,0.0,5.0,0))
        assertTrue(ManeuverNotice.visible(100.0,95.0,0.0,5.0,0))
    }
    @Test fun noticeClearsAfterPassingIntersection() {
        assertTrue(ManeuverNotice.visible(100.0,103.0,0.0,5.0,0))
        assertFalse(ManeuverNotice.visible(100.0,106.0,0.0,5.0,0))
    }
    @Test fun nearbyParallelRoadAndBadFixDoNotTriggerNotice() {
        assertFalse(ManeuverNotice.visible(100.0,98.0,30.0,5.0,0))
        assertFalse(ManeuverNotice.visible(100.0,98.0,0.0,50.0,0))
        assertFalse(ManeuverNotice.visible(100.0,98.0,0.0,5.0,16000))
        assertFalse(ManeuverNotice.visible(100.0,98.0,0.0,5.0,-10))
    }
    @Test fun proximityUsesRemainingRoadDistanceInsteadOfStraightLine() {
        val road=listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.001,0.0),RouteParser.Coordinate(.001,.00003),RouteParser.Coordinate(0.0,.00003))
        val fix=RouteMonitor.match(road,0.0,0.0)!!
        val turn=RouteMonitor.match(road,0.0,.00003,230.0)!!
        assertTrue(turn.along-fix.along>200)
        assertFalse(ManeuverNotice.visible(turn.along,fix.along,fix.distance,5.0,0))
    }
}
