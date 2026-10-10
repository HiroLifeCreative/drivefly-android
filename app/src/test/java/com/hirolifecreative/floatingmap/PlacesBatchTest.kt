package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class PlacesBatchTest {
 @Test fun partialBatchUsesIdsAndMissingHoursRemainUnknown() {
  val records = PlacesClient.parseBatch(JSONObject("""{"results":[{"mapbox_id":"a","opening_hours":"24/7"},{"mapbox_id":"b","permanently_closed":true}],"missing":["c"]}"""))
  assertEquals(2,records.size)
  assertEquals("24/7",records["a"]?.hours)
  assertNull(records["b"]?.hours)
  assertTrue(records["b"]!!.permanentlyClosed)
  assertFalse(records.containsKey("c"))
 }
 @Test fun sparseDetailsDoNotErasePublishedHours() {
  val old = PlacesClient.Details("Mo-Su 09:00-22:00",null,false,null)
  val sparse = PlacesClient.Details(null,"America/New_York",false,null)
  val merged = PlacesClient.mergeDetails(old,sparse)
  assertEquals(old.hours,merged.hours)
  assertEquals("America/New_York",merged.timezone)
 }
 @Test fun resultsFromSeveralAreasAreDeduplicatedById() {
  val a = PlacesClient.Business("Restaurant","A",-81.0,29.0,"a")
  val b = PlacesClient.Business("Restaurant","B",-81.1,29.1,"b")
  assertEquals(listOf(a,b),PlacesClient.unique(listOf(a,b,a,b)))
 }
 @Test fun searchResponsePreservesPublishedHours() {
  val body = JSONObject("""{"features":[{"geometry":{"coordinates":[-81.0,29.0]},"properties":{"name":"Local","mapbox_id":"a","opening_hours":"24/7"}}]}""")
  assertEquals("24/7",PlacesClient.parse(body).single().publishedDetails?.hours)
 }
}
