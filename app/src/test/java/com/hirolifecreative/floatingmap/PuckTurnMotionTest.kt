package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class PuckTurnMotionTest {
 @Test fun followsRealTurnDirectionAndLimitsRotation() {
  assertEquals(-14.0,PuckTurnMotion.target(90.0,0.0,20.0),.001)
  assertEquals(14.0,PuckTurnMotion.target(0.0,90.0,20.0),.001)
  assertEquals(4.5,PuckTurnMotion.target(355.0,5.0,20.0),.001)
 }
 @Test fun returnsStraightAfterTurnAndIgnoresStationaryNoise() {
  assertEquals(0.0,PuckTurnMotion.target(90.0,90.0,20.0),.001)
  assertEquals(0.0,PuckTurnMotion.target(90.0,120.0,0.0),.001)
  assertEquals(0.0,PuckTurnMotion.target(90.0,91.0,20.0),.001)
  assertEquals(0.0,PuckTurnMotion.target(Double.NaN,90.0,20.0),.001)
 }
 @Test fun markerAndCameraShareTheLowerAnchor() {
  assertEquals(NavigationScreenAnchor.FRACTION,(1+NavigationScreenAnchor.TOP_PADDING-NavigationScreenAnchor.BOTTOM_PADDING)/2,.00001)
 }
}
