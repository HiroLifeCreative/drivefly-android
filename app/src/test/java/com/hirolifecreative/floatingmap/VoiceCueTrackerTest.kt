package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class VoiceCueTrackerTest {
 private fun cue(t: VoiceCueTracker,d: Double,now: Long=0,step: Int=1,speed: Double=0.0,route: String="r")=
  t.maneuver(route,step,"Gira a la derecha.","Calle Uno",d,speed,now)
 @Test fun farTurnSaysDistanceAndNeverImmediateCommand() {
  val t=VoiceCueTracker()
  assertEquals("Continúa durante una milla. Después, gira a la derecha, hacia Calle Uno.",cue(t,1609.344))
  assertNull(cue(t,1500.0,10000))
 }
 @Test fun announcesApproachAndImmediateOnlyWhenNear() {
  val t=VoiceCueTracker()
  assertEquals("En 500 metros, gira a la derecha, hacia Calle Uno.",cue(t,500.0))
  assertNull(cue(t,450.0,10000))
  assertEquals("En 200 metros, gira a la derecha, hacia Calle Uno.",cue(t,200.0,20000))
  assertEquals("Gira a la derecha, hacia Calle Uno.",cue(t,20.0,21000))
  assertNull(cue(t,15.0,22000))
 }
 @Test fun nextFarTurnWaitsAfterPreviousTurnAndIncludesDistance() {
  val t=VoiceCueTracker();cue(t,10.0)
  assertNull(cue(t,4000.0,2000,step=2))
  assertTrue(cue(t,3900.0,10000,step=2)!!.startsWith("Continúa durante"))
 }
 @Test fun speedAllowsUsefulAdvanceForNearCue() {
  val t=VoiceCueTracker()
  assertTrue(cue(t,65.0,speed=20.0)!!.startsWith("Gira"))
  assertTrue(cue(VoiceCueTracker(),90.0,speed=20.0)!!.startsWith("En"))
 }
 @Test fun gpsJitterAndRepeatedMapsDoNotRepeatStages() {
  val t=VoiceCueTracker();cue(t,190.0)
  assertNull(cue(t,220.0,10000));assertNull(cue(t,180.0,20000))
 }
 @Test fun passedOrInvalidManeuverNeverSpeaks() {
  val t=VoiceCueTracker();assertNull(cue(t,-10.0));assertNull(cue(t,Double.NaN))
  assertNotNull(cue(t,20.0))
 }
 @Test fun rerouteAndResetAllowNewCueWithoutDuplicatedRoad() {
  val t=VoiceCueTracker();cue(t,500.0)
  assertNotNull(cue(t,500.0,10000,route="new"));t.reset();assertNotNull(cue(t,500.0))
  assertEquals("En 500 metros, gira hacia Calle Uno.",t.maneuver("third",1,"Gira hacia Calle Uno.","Calle Uno",500.0,0.0,10000))
 }
}
