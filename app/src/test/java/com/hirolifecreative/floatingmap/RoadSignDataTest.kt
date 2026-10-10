package com.hirolifecreative.floatingmap
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class RoadSignDataTest {
 @Test fun filtersUnsupportedInvalidAndDuplicateNodes() {
  val body = JSONObject("""{"elements":[
   {"type":"node","id":1,"lat":29.8,"lon":-81.3,"tags":{"highway":"stop"}},
   {"type":"node","id":1,"lat":29.8,"lon":-81.3,"tags":{"highway":"stop"}},
   {"type":"node","id":2,"lat":29.8,"lon":-81.3,"tags":{"highway":"traffic_signals"}},
   {"type":"node","id":3,"lat":290,"lon":-81.3,"tags":{"highway":"stop"}},
   {"type":"node","id":4,"lon":-81.3,"tags":{"highway":"stop"}},
   {"type":"way","id":5,"lat":29.8,"lon":-81.3,"tags":{"highway":"stop"}},
   {"type":"node","id":6,"lat":29.8,"lon":-81.3,"tags":{"highway":"residential"}}]}""")
  assertEquals(listOf("stop","traffic_signals"),RoadSignData.parse(body).map { it.kind })
 }
 @Test fun supportsAdditionalRoadsideSigns() {
  val body = JSONObject("""{"elements":[{"type":"node","id":1,"lat":0,"lon":0,"tags":{"highway":"give_way"}},{"type":"node","id":2,"lat":1,"lon":1,"tags":{"highway":"crossing"}},{"type":"node","id":3,"lat":2,"lon":2,"tags":{"highway":"speed_camera"}}]}""")
  assertEquals(3,RoadSignData.parse(body).size)
 }
}
