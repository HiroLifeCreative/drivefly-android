package com.hirolifecreative.floatingmap
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
class WeatherDataTest {
 private val now=Instant.parse("2026-10-07T20:00:00Z").toEpochMilli()
 private val point=DestinationAccess.Point(29.8,-81.3)
 private fun data(weather: String="""{"weather":"rain","intensity":"light"}""",time: String="2026-10-07T19:45:00Z")=JSONObject("""{"geometry":{"coordinates":[-81.3,29.8]},"properties":{"timestamp":"$time","presentWeather":[$weather]}}""")
 @Test fun currentRainAndSnowHaveSubtleBoundedIntensity() {
  val rain=WeatherData.parse(data(),now)!!;assertEquals(WeatherData.Kind.RAIN,rain.kind);assertEquals(.10,rain.intensity,0.0)
  val snow=WeatherData.parse(data("""{"weather":"snow","intensity":"heavy"}"""),now)!!;assertEquals(WeatherData.Kind.SNOW,snow.kind);assertEquals(.25,snow.intensity,0.0)
 }
 @Test fun missingObservationNeverInventsWeather() { assertNull(WeatherData.parse(JSONObject(),now)) }
 @Test fun oldAndFutureObservationsAreRejected() {
  assertNull(WeatherData.parse(data(time="2026-10-07T18:00:00Z"),now));assertNull(WeatherData.parse(data(time="2026-10-07T20:10:00Z"),now))
 }
 @Test fun noPrecipitationClearsEffects() { assertEquals(WeatherData.Kind.NONE,WeatherData.parse(data(""),now)!!.kind) }
 @Test fun effectsExpireOrDisappearOutsideStationRegion() {
  val observation=WeatherData.parse(data(),now)!!
  assertTrue(WeatherData.applicable(observation,point,now));assertFalse(WeatherData.applicable(observation,DestinationAccess.Point(30.8,-81.3),now));assertFalse(WeatherData.applicable(observation,point,now+5400001))
 }
}
