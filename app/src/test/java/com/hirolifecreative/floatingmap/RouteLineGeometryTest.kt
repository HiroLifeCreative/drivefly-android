package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class RouteLineGeometryTest {
 private val points = listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.001,0.0),RouteParser.Coordinate(.002,0.0))
 @Test fun previewOrZeroProgressKeepsEntireRoute() { assertEquals(points,RouteLineGeometry.remaining(points,0.0)) }
 @Test fun cutsInsideSegmentRatherThanLeavingLineBehind() {
  val result = RouteLineGeometry.remaining(points,55.5974633)
  assertEquals(.0005,result.first().longitude,.0000001)
  assertEquals(points.last(),result.last())
  assertEquals(3,result.size)
 }
 @Test fun passedVerticesAreRemoved() {
  val result = RouteLineGeometry.remaining(points,166.7923899)
  assertEquals(.0015,result.first().longitude,.0000001)
  assertEquals(2,result.size)
 }
 @Test fun finishHasNoRemainingLine() { assertTrue(RouteLineGeometry.remaining(points,250.0).isEmpty()) }
 @Test fun repeatedVertexIsSafe() {
  val result = RouteLineGeometry.remaining(listOf(points[0],points[0],points[1]),55.5974633)
  assertEquals(.0005,result.first().longitude,.0000001)
 }
}
