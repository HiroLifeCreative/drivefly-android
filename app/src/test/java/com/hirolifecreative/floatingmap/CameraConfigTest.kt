package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class CameraConfigTest {
 @Test fun defaultsPreserveRequestedSixtyDegreesAndLastDistance() {
  assertEquals(60,CameraConfig().tilt)
  assertEquals(17.145,CameraConfig().zoom,0.0)
 }
 @Test fun twiceTheVisibleDistanceReducesZoomByOne() {
  assertEquals(CameraConfig().zoom-1,CameraConfig(distancePercent=200).zoom,.000001)
 }
 @Test fun closerViewUsesHigherZoom() {
  assertEquals(CameraConfig().zoom+1,CameraConfig(distancePercent=50).zoom,.000001)
 }
 @Test fun persistedValuesCannotExceedSupportedSliderRanges() {
  assertEquals(CameraConfig(75,50),CameraConfig(99,-20).normalized())
  assertEquals(CameraConfig(0,1000),CameraConfig(-10,1500).normalized())
 }
 @Test fun expandedDistanceSupportsTenTimesDefaultReach() {
  assertEquals(1000,CameraConfig(distancePercent=1000).normalized().distancePercent)
  assertEquals(13.8230719051,CameraConfig(distancePercent=1000).zoom,.000001)
 }
}
