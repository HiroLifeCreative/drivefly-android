package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class PlacesClientTest {
 @Test fun preservesBusinessIdentityAndRejectsInvalidCoordinates() {
  val list=PlacesClient.parse(JSONObject("""{"features":[{"geometry":{"coordinates":[-81.4,29.8]},"properties":{"name":"Cafe","full_address":"Main St","mapbox_id":"branch-123"}},{"geometry":{"coordinates":[-81.4,129.8]},"properties":{"name":"Invalid"}}]}"""))
  assertEquals(1,list.size);assertEquals("branch-123",list.single().id);assertEquals("Main St",list.single().address)
 }
}
