package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class SmoothValueTest {
 @Test fun forwardTargetConvergesWithoutOvershoot() {
  val motion = SmoothValue()
  var previous = 0.0
  repeat(300) {
   val value = motion.advance(100.0,1.0/60)
   assertTrue(value >= previous);assertTrue(value <= 100)
   previous = value
  }
  assertEquals(100.0,motion.value,.002)
 }
 @Test fun newFixPreservesVelocityInsteadOfRestartingAtZero() {
  val motion = SmoothValue()
  repeat(20) { motion.advance(10.0,1.0/60) }
  val previous = motion.value;val speed = motion.velocity
  motion.advance(20.0,1.0/60)
  assertTrue(motion.value > previous)
  assertTrue(motion.velocity > speed*.9)
 }
 @Test fun equalElapsedTimeGivesSameResultAt30And60Frames() {
  val fast = SmoothValue();val slow = SmoothValue()
  repeat(60) { fast.advance(100.0,1.0/60) }
  repeat(30) { slow.advance(100.0,1.0/30) }
  assertEquals(fast.value,slow.value,.000001)
  assertEquals(fast.velocity,slow.velocity,.000001)
 }
 @Test fun compassCrossesNorthThroughTwoDegreesNot358() {
  assertEquals(361.0,SmoothValue.nearestAngle(359.0,1.0),0.0)
  assertEquals(-1.0,SmoothValue.nearestAngle(1.0,359.0),0.0)
 }
 @Test fun reversingTargetBrakesProgressivelyThenReverses() {
  val motion = SmoothValue()
  repeat(30) { motion.advance(10.0,1.0/60) }
  val speed = motion.velocity
  motion.advance(-10.0,1.0/60)
  assertTrue(motion.velocity > 0 && motion.velocity < speed)
  repeat(300) { motion.advance(-10.0,1.0/60) }
  assertEquals(-10.0,motion.value,.002)
 }
 @Test fun invalidTimeOrTargetDoesNotMoveCamera() {
  val motion = SmoothValue(5.0)
  assertEquals(5.0,motion.advance(10.0,0.0),0.0)
  assertEquals(5.0,motion.advance(Double.NaN,.016),0.0)
  assertEquals(5.0,motion.advance(10.0,Double.NaN),0.0)
 }
 @Test fun compassRemainsSmoothAfterSeveralFullRotations() {
  assertEquals(1079.0,SmoothValue.nearestAngle(1081.0,359.0),0.0)
  assertEquals(-1079.0,SmoothValue.nearestAngle(-1081.0,1.0),0.0)
 }
}
