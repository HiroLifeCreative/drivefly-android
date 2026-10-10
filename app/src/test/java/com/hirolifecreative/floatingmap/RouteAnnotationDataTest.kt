package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class RouteAnnotationDataTest {
 @Test fun missingAnnotationsDoNotCreateLimitsOrTraffic() {
  val data=RouteAnnotationData.parse(JSONObject("""{"legs":[{}]}"""),3);assertTrue(data.limitsMph.isEmpty());assertNull(data.meanCongestion)
 }
 @Test fun unknownAndUnlimitedNeverBecomeZeroSpeedLimit() {
  val data=RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"maxspeed":[{"unknown":true},{"none":true}]}}]}"""),3)
  assertEquals(listOf(null,null),data.limitsMph)
 }
 @Test fun unitsAreConvertedWithoutFabricatingMissingUnits() {
  val data=RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"maxspeed":[{"speed":25,"unit":"mph"},{"speed":80,"unit":"km/h"},{"speed":30}]}}]}"""),4)
  assertEquals(25.0,data.limitsMph[0]!!,0.001);assertEquals(49.7097,data.limitsMph[1]!!,0.001);assertNull(data.limitsMph[2])
 }
 @Test fun mismatchedGeometryDoesNotAssociateSpeedWithWrongRoad() {
  val data=RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"maxspeed":[{"speed":25,"unit":"mph"}]}}]}"""),4);assertTrue(data.limitsMph.isEmpty())
 }
 @Test fun trafficRequiresCoverageAndUsesDistanceWeights() {
  val data=RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"distance":[100,300],"congestion_numeric":[0,80]}}]}"""),3)
  assertEquals(60.0,data.meanCongestion!!,0.001)
  assertNull(RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"distance":[100,300],"congestion_numeric":[0,null]}}]}"""),3).meanCongestion)
 }
 @Test fun partiallyAnnotatedLegsDoNotClaimLessTraffic() {
  assertNull(RouteAnnotationData.parse(JSONObject("""{"legs":[{"annotation":{"distance":[100],"congestion_numeric":[10]}},{}]}"""),3).meanCongestion)
 }
 @Test fun speedLimitComesFromCurrentSegmentAndNotNearbyOffRouteRoad() {
  val route=RouteParser.Route(listOf(RouteParser.Coordinate(0.0,0.0),RouteParser.Coordinate(.001,0.0),RouteParser.Coordinate(.002,0.0)),emptyList())
  val result=RoutingClient.Result("",10.0,222.0,route,annotation=RouteAnnotationData(listOf(25.0,35.0)))
  assertEquals(25.0,RouteAnnotationData.limit(result,.0005,0.0)!!,0.001)
  assertEquals(35.0,RouteAnnotationData.limit(result,.0015,0.0)!!,0.001)
  assertNull(RouteAnnotationData.limit(result,.0015,.002))
 }
}
