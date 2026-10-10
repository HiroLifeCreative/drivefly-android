package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class SpeedWarningTest {
 @Test fun onlyWarnsAboveActualLimit() {
  assertFalse(SpeedWarning.active(45.0,45.0))
  assertFalse(SpeedWarning.active(44.9,45.0))
  assertTrue(SpeedWarning.active(45.1,45.0))
 }
 @Test fun missingInvalidAndUnlimitedRoadsDoNotWarn() {
  for(limit in listOf(null,0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) assertFalse(SpeedWarning.active(60.0,limit))
  assertFalse(SpeedWarning.active(null,45.0))
  assertFalse(SpeedWarning.active(Double.NaN,45.0))
 }
 @Test fun pulseIsContinuousBoundedAndRepeatsWithoutGoingBlack() {
  assertEquals(SpeedWarning.pulse(0),SpeedWarning.pulse(1200),.0001f)
  assertTrue(SpeedWarning.pulse(0)>SpeedWarning.pulse(600))
  for(t in 0L..2400L) assertTrue(SpeedWarning.pulse(t) in .29f..1.01f)
 }
}
