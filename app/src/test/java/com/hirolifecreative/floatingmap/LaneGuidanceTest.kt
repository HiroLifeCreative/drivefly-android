package com.hirolifecreative.floatingmap
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class LaneGuidanceTest {
 private val location=RouteParser.Coordinate(-81.3,29.8)
 private fun step(lanes: String,lon: Double=-81.3)=JSONObject("""{"intersections":[{"location":[$lon,29.8],"lanes":[$lanes]}]}""")
 @Test fun preservesLaneOrderAndHighlightsRecommendedLane() {
  val lanes=LaneGuidance.parse(step("""{"valid":false,"indications":["left"]},{"valid":true,"active":true,"valid_indication":"right","indications":["straight","right"]},{"valid":true,"active":false,"indications":["right"]}"""),location)
  assertEquals(listOf("left"),lanes[0].directions);assertTrue(LaneGuidance.recommended(lanes,1));assertFalse(LaneGuidance.recommended(lanes,2));assertEquals("right",lanes[1].routeDirection)
 }
 @Test fun validLanesRemainUsableWithoutActiveField() {
  val lanes=LaneGuidance.parse(step("""{"valid":true,"indications":["straight"]},{"valid":true,"indications":["straight"]}"""),location)
  assertTrue(LaneGuidance.recommended(lanes,0));assertTrue(LaneGuidance.recommended(lanes,1))
 }
 @Test fun unrelatedIntersectionAndUnknownValidityAreOmitted() {
  assertTrue(LaneGuidance.parse(step("""{"valid":true,"indications":["left"]}""",lon=-81.31),location).isEmpty())
  assertTrue(LaneGuidance.parse(step("""{"indications":["left"]}"""),location).isEmpty())
 }
 @Test fun unavailableGuidanceDoesNotInventLanes() {
  assertTrue(LaneGuidance.parse(JSONObject(),location).isEmpty())
  assertTrue(LaneGuidance.parse(step("""{"valid":true,"indications":["unknown"]}"""),location).isEmpty())
 }
 @Test fun invalidDirectionCannotOverrideAllowedLaneDirections() {
  val lanes=LaneGuidance.parse(step("""{"valid":true,"valid_indication":"right","indications":["straight"]}"""),location)
  assertNull(lanes.single().routeDirection)
 }
}
