package com.hirolifecreative.floatingmap
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class CameraConfigCodecTest {
 @Test fun oldSettingsKeepTheirCameraAndDoNotEnableNewFeatures() {
  val c=CameraConfigCodec.decode(JSONObject("""{"tilt":55,"distancePercent":350}"""))
  assertEquals(55,c.tilt);assertEquals(350,c.distancePercent)
  assertFalse(c.speedZoomEnabled);assertFalse(c.nativePuck3D);assertEquals(0,c.puckTilt);assertEquals(100,c.puckSizePercent)
 }
 @Test fun allAdjustmentsSurviveStorageAndPreviewSerialization() {
  val c=CameraConfig(50,250,true,20,70,-30,40,true,35,180)
  assertEquals(c,CameraConfigCodec.decode(JSONObject(CameraConfigCodec.encode(c).toString())))
 }
 @Test fun savedPuckSizeIsClampedWithoutChangingCameraSettings() {
  val large=CameraConfigCodec.decode(JSONObject("""{"tilt":60,"distancePercent":350,"puckSizePercent":999}"""))
  assertEquals(250,large.puckSizePercent);assertEquals(60,large.tilt);assertEquals(350,large.distancePercent)
  assertEquals(50,CameraConfigCodec.decode(JSONObject("""{"puckSizePercent":0}""")).puckSizePercent)
 }
}
