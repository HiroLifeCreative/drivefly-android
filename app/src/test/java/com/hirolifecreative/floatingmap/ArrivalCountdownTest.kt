package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class ArrivalCountdownTest {
 private fun arrived(): ArrivalCountdown = ArrivalCountdown().apply {
  observe(0,true,false);observe(1000,true,false);observe(2000,true,false)
 }
 @Test fun finishesTwentySecondsAfterConfirmedArrival() {
  val a=arrived()
  assertEquals(20,a.secondsLeft(2000))
  assertFalse(a.due(21999));assertTrue(a.due(22000))
 }
 @Test fun oneNearbyReadingOrBrokenSequenceDoesNotConfirmArrival() {
  val a=ArrivalCountdown();a.observe(0,true,false);a.observe(1000,false,false);a.observe(2000,true,false)
  assertNull(a.deadline);assertFalse(a.due(999999))
 }
 @Test fun jitterDoesNotRestartCountdownButDepartureCancelsIt() {
  val a=arrived();a.observe(8000,false,false)
  assertEquals(14,a.secondsLeft(8000))
  a.observe(9000,false,true);assertNull(a.deadline);assertFalse(a.due(22000))
 }
 @Test fun manualFinishOrNewDestinationCanResetDeadline() {
  val a=arrived();a.reset();assertFalse(a.due(22000))
  a.observe(30000,true,false);a.observe(31000,true,false);a.observe(32000,true,false)
  assertFalse(a.due(51999));assertTrue(a.due(52000))
 }
}
