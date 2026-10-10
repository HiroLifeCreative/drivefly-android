package com.hirolifecreative.floatingmap
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class DestinationAccessTest {
 private val center=DestinationAccess.Point(29.8,-81.3)
 private fun feature(points: String)=JSONObject("""{"routable_points":[$points]}""")
 private val entrance="""{"name":"entrance","accuracy":"high","latitude":29.8001,"longitude":-81.3}"""
 private val vehicle="""{"name":"default","latitude":29.8,"longitude":-81.3001}"""
 @Test fun separatesDoorAndVehiclePoint() {
  val access=DestinationAccess.parse(feature("$entrance,$vehicle"),center)
  assertTrue(access.hasEntrance);assertEquals(29.8001,access.door.lat,0.0);assertEquals(-81.3001,access.vehicle.lon,0.0)
 }
 @Test fun missingEntranceKeepsAddressAndVehicleAccess() {
  val access=DestinationAccess.parse(feature(vehicle),center)
  assertFalse(access.hasEntrance);assertEquals(center,access.door);assertEquals(-81.3001,access.vehicle.lon,0.0)
 }
 @Test fun lowAccuracyOrAmbiguousDoorsNeverBecomeCertainEntrance() {
  assertFalse(DestinationAccess.parse(feature(entrance.replace("high","low")),center).hasEntrance)
  assertFalse(DestinationAccess.parse(feature("$entrance,"+entrance.replace("29.8001","29.8005")),center).hasEntrance)
 }
 @Test fun rejectsInvalidAndUnrelatedEntranceCoordinates() {
  assertFalse(DestinationAccess.parse(feature(entrance.replace("29.8001","95")),center).hasEntrance)
  assertFalse(DestinationAccess.parse(feature(entrance.replace("29.8001","29.9")),center).hasEntrance)
 }
 @Test fun supportsLegacyRoutablePointObject() {
  val body=JSONObject("""{"routable_points":{"points":[{"name":"default","coordinates":[-81.3001,29.8]},$entrance]}}""")
  val access=DestinationAccess.parse(body,center);assertTrue(access.hasEntrance);assertEquals(-81.3001,access.vehicle.lon,0.0)
 }
 @Test fun toggleDisablesDoorSelectionWithoutLosingVehicleAccess() {
  val access=DestinationAccess.parse(feature("$entrance,$vehicle"),center,false)
  assertEquals(center,access.door);assertFalse(access.hasEntrance);assertEquals(-81.3001,access.vehicle.lon,0.0)
 }
}
