package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class VoicePreferencesTest {
 @Test fun defaultsAndSavedSettingsPreserveSelectedVoice() {
  assertEquals(VoiceConfig(),VoicePreferences.decode(JSONObject()))
  for (mode in listOf("claude","android","off")) {
   val c=VoiceConfig(mode,110);assertEquals(c,VoicePreferences.decode(VoicePreferences.encode(c)))
  }
 }
 @Test fun invalidSettingsAreLimitedToSupportedValues() {
  assertEquals(VoiceConfig("claude",120),VoicePreferences.decode(JSONObject("""{"mode":"bad","speedPercent":500}""")))
  assertEquals(80,VoiceConfig("off",0).normalized().speedPercent)
 }
 @Test fun orderAndArrivalTogglesPersistWithoutChangingTheVoice() {
  val config=VoiceConfig("claude",110,orderAnnouncements=false,arrivalDetails=false)
  assertEquals(config,VoicePreferences.decode(VoicePreferences.encode(config)))
 }
}
