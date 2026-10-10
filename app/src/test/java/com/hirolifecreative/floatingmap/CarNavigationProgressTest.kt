package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class CarNavigationProgressTest {
 private val steps=listOf(500.0,1000.0,0.0).map { RouteParser.Step("Gira","",it,RouteParser.Coordinate(0.0,0.0)) }
 @Test fun usesSharedAlongRouteProgressRatherThanStraightLineDistance() {
  assertEquals(300.0,CarNavigationProgress.metersToStep(steps,1,1300.0),.001)
  assertEquals(900.0,CarNavigationProgress.metersToStep(steps,2,900.0),.001)
 }
 @Test fun passedTurnCannotShowNegativeDistance() {
  assertEquals(0.0,CarNavigationProgress.metersToStep(steps,1,800.0),.001)
 }
 @Test fun missingProgressStartsFromRouteOrigin() {
  assertEquals(500.0,CarNavigationProgress.metersToStep(steps,1,null),.001)
 }
}
