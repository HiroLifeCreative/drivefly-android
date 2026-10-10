package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class RouteVisualTrackerTest {
 private val road = listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.01,0.0))
 @Test fun alternatingLateralGpsNoiseStaysExactlyOnRoad() {
  val tracker = RouteVisualTracker()
  repeat(8) { i ->
   val value = tracker.update(road,.002+i*.0001,if (i%2==0) .0001 else -.0001,15.0,8.0,if (i%2==0) 75.0 else 105.0)!!
   assertEquals(0.0,value.coordinate.latitude,0.0)
   assertEquals(90.0,value.heading,.001)
   assertTrue(value.snapped)
  }
 }
 @Test fun stoppedGpsWanderDoesNotChangeLocationOrHeading() {
  val tracker = RouteVisualTracker()
  val first = tracker.update(road,.002,0.0,10.0,0.0,90.0)!!
  repeat(6) { i -> assertEquals(first,tracker.update(road,.002+i*.00001,.00005,15.0,0.0,i*45.0)) }
 }
 @Test fun badFixKeepsPreviousDisplay() {
  val tracker = RouteVisualTracker()
  val first = tracker.update(road,.002,0.0,10.0,8.0,90.0)
  assertEquals(first,tracker.update(road,.009,.01,150.0,8.0,270.0))
 }
 @Test fun actualDeviationIsNotHiddenBySnapping() {
  val tracker = RouteVisualTracker()
  val result = tracker.update(road,.002,.001,5.0,8.0,90.0)!!
  assertFalse(result.snapped)
  assertEquals(.001,result.coordinate.latitude,0.0)
 }
 @Test fun reverseDirectionRemainsAvailableForRerouting() {
  val result = RouteVisualTracker().update(road,.002,.0001,5.0,8.0,270.0)!!
  assertEquals(270.0,result.heading,0.0)
 }
 @Test fun animationAtCornerStaysOnRouteRatherThanCuttingDiagonally() {
  val corner = listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.001,0.0),RouteParser.Coordinate(.001,.001))
  val point = RouteLineGeometry.pointAt(corner,150.0)!!
  assertEquals(.001,point.longitude,0.0)
  assertTrue(point.latitude > 0)
 }
 @Test fun forwardDrivingSmallBackwardNoiseDoesNotRegrowPassedLine() {
  val tracker = RouteVisualTracker()
  val first = tracker.update(road,.003,0.0,10.0,8.0,90.0)!!
  assertEquals(first,tracker.update(road,.00298,.00005,10.0,8.0,90.0))
 }
 @Test fun actualReverseMovementIsNotClampedAsGpsNoise() {
  val tracker = RouteVisualTracker()
  tracker.update(road,.003,0.0,10.0,8.0,90.0)
  val reverse = tracker.update(road,.00298,0.0,10.0,8.0,270.0)!!
  assertEquals(270.0,reverse.heading,0.0)
  assertTrue(reverse.coordinate.longitude < .003)
 }
}
