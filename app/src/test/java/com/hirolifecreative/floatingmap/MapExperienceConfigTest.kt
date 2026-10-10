package com.hirolifecreative.floatingmap
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class MapExperienceConfigTest {
 @Test fun freshConfigurationEnablesAvailableFeatures() {
  val c=MapExperienceConfig.parse(JSONObject());assertTrue(c.enabled&&c.trees&&c.buildings&&c.landmarks&&c.facades&&c.detailedRoads&&c.entrances&&c.laneGuidance)
 }
 @Test fun themeFollowsAppearanceAndManualPresetWins() {
  assertEquals("day",MapExperienceConfig().lightPreset(false,0));assertEquals("night",MapExperienceConfig().lightPreset(true,12))
  assertEquals("dusk",MapExperienceConfig(lighting="dusk").lightPreset(false,12))
 }
 @Test fun clockPresetsChangeAtTheirBoundaries() {
  val c=MapExperienceConfig(lighting="clock")
  assertEquals("night",c.lightPreset(false,5));assertEquals("dawn",c.lightPreset(false,6));assertEquals("day",c.lightPreset(false,8));assertEquals("dusk",c.lightPreset(false,18));assertEquals("night",c.lightPreset(false,20))
 }
 @Test fun roundTripRetainsDisabledFeaturesAndUnknownPresetFallsBack() {
  val c=MapExperienceConfig(false,false,false,false,false,false,false,false,"clock")
  assertEquals(c,MapExperienceConfig.parse(c.json()))
  assertEquals("theme",MapExperienceConfig.parse(JSONObject("""{"lighting":"weather"}""")).lighting)
 }
}
