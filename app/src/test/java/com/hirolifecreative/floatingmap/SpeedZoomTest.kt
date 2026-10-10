package com.hirolifecreative.floatingmap
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.ln
class SpeedZoomTest {
 private val config=CameraConfig(speedZoomEnabled=true)
 @Test fun defaultBandsHaveExactThresholdsAndRelativeDistanceChanges() {
  val farther=config.zoom-ln(1.2)/ln(2.0)
  val closer=config.zoom-ln(.8)/ln(2.0)
  assertEquals(farther,SpeedZoom.target(config,0.0),.000001)
  assertEquals(farther,SpeedZoom.target(config,15.0),.000001)
  assertEquals(config.zoom,SpeedZoom.target(config,15.001),.000001)
  assertEquals(config.zoom,SpeedZoom.target(config,60.0),.000001)
  assertEquals(closer,SpeedZoom.target(config,60.001),.000001)
 }
 @Test fun disabledOrMissingSpeedRestoresConfiguredBase() {
  val c=config.copy(distancePercent=500)
  for(speed in listOf(null,Double.NaN,-1.0)) assertEquals(c.zoom,SpeedZoom.target(c,speed),.000001)
  assertEquals(c.zoom,SpeedZoom.target(c.copy(speedZoomEnabled=false),90.0),.000001)
 }
 @Test fun rulesCanBeReversedAndChangedWithoutChangingTheSavedBase() {
  val c=config.copy(distancePercent=300,slowMph=25,fastMph=80,slowDistanceChange=-50,fastDistanceChange=100)
  assertEquals(c.zoom+1,SpeedZoom.target(c,20.0),.000001)
  assertEquals(c.zoom,SpeedZoom.target(c,50.0),.000001)
  assertEquals(c.zoom-1,SpeedZoom.target(c,90.0),.000001)
  assertEquals(300,c.distancePercent)
 }
 @Test fun invalidSettingsCannotProduceOverlappingBandsOrNegativeScale() {
  val c=config.copy(slowMph=200,fastMph=-1,slowDistanceChange=-100,fastDistanceChange=900,puckTilt=90).normalized()
  assertEquals(119,c.slowMph);assertEquals(120,c.fastMph)
  assertEquals(-80,c.slowDistanceChange);assertEquals(200,c.fastDistanceChange);assertEquals(60,c.puckTilt)
  assertTrue(SpeedZoom.target(c,0.0).isFinite())
 }
}
