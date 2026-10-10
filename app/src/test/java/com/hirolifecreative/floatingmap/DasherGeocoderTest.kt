package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class DasherGeocoderTest {
 private val origin = DasherGeocoder.Coordinate(29.8,-81.3)
 @Test fun rejectsWrongHouseNumberAndLowConfidence() {
  assertNull(DasherGeocoder.parse(JSONObject("""{"features":[{"address":"999","relevance":1,"center":[-81.3,29.8]}]}"""),"123 Main St",origin))
  assertNull(DasherGeocoder.parse(JSONObject("""{"features":[{"address":"123","relevance":0.2,"center":[-81.3,29.8]}]}"""),"123 Main St",origin))
 }
 @Test fun acceptsMatchingLocalAddress() {
  assertEquals(origin,DasherGeocoder.parse(JSONObject("""{"features":[{"address":"123","relevance":1,"center":[-81.3,29.8]}]}"""),"123 Main St",origin))
 }
 @Test fun equallyLikelyDistinctAddressesRemainUnresolved() {
  assertNull(DasherGeocoder.parse(JSONObject("""{"features":[{"address":"123","relevance":1,"center":[-81.29,29.8]},{"address":"123","relevance":1,"center":[-81.31,29.8]}]}"""),"123 Main St",origin))
 }
 @Test fun removesSuiteWithoutRemovingHighwayDirectionOrCity() {
  assertEquals("1820 Us Highway 1 S, St Augustine FL 32084-6017",DasherGeocoder.searchAddress("1820 Us Highway 1 S # S, St Augustine FL 32084-6017 United States"))
 }
 @Test fun prefixStreetDoesNotNeedSuffix() {
  assertEquals("3237 Cll Cortez, St. Augustine, FL 32086",DasherGeocoder.searchAddress("3237 Cll Cortez, St. Augustine, FL 32086"))
 }
 private fun business(name: String = "Chase Bank",zip: String = "32084",lon: Double = -81.3) = JSONObject("""{"properties":{"name":"$name","full_address":"100 Main St, St. Augustine, FL $zip","context":{"postcode":{"name":"$zip"},"place":{"name":"St. Augustine"}}},"geometry":{"coordinates":[$lon,29.8]}}""")
 private fun businesses(vararg items: JSONObject) = JSONObject().put("features",org.json.JSONArray(items.toList()))
 @Test fun resolvesNamedBusinessWithMatchingCityAndZip() {
  assertEquals(origin,DasherGeocoder.parseNamedPlace(businesses(business()),"Chase Bank, Saint Augustine, FL 32084",origin))
 }
 @Test fun rejectsOtherBranchWrongNameAndAmbiguousBranches() {
  val query = "Chase Bank, Saint Augustine, FL 32084"
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(zip="32086")),query,origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Other Bank")),query,origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(),business(lon=-81.32)),query,origin))
 }

 @Test fun resolvesInstitutionAliasesWithoutChangingLocalityRules() {
  val query = "Flagler College Fec Dorms, Saint Augustine, FL 32084"
  assertEquals(origin,DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College FEC Residence Halls")),query,origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College")),query,origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College FEC Residence Halls",zip="32086")),query,origin))
 }
 @Test fun specificCampusTowerCannotResolveToOtherTowerOrMainCampus() {
  val query = "Flagler College Fec Dorms · FEC Tower- C, Saint Augustine, FL 32084"
  assertEquals(origin,DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College FEC Tower C")),query,origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College FEC Tower A"),business(name="Flagler College FEC Dorms")),query,origin))
 }
 @Test fun supportsAllNamedInstitutionCategories() {
  for (name in listOf("Chase Bank", "Saint Augustine High School", "Flagler Hospital", "Anastasia State Park", "Saint Augustine Public Library")) {
   assertEquals(origin,DasherGeocoder.parseNamedPlace(businesses(business(name=name)),"$name, Saint Augustine, FL 32084",origin))
  }
 }
 @Test fun preferredNamesAreSearchableAndDifferentSchoolsRemainDistinct() {
  val place = business(name="FEC Housing");place.getJSONObject("properties").put("name_preferred","Flagler College FEC Residence Hall")
  assertEquals(origin,DasherGeocoder.parseNamedPlace(businesses(place),"Flagler College FEC Dorms, Saint Augustine, FL 32084",origin))
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Saint Augustine Elementary School")),"Saint Augustine High School, Saint Augustine, FL 32084",origin))
 }
 @Test fun aliasesStillRejectAmbiguousBranches() {
  val query="Flagler College FEC Dorms, Saint Augustine, FL 32084"
  assertNull(DasherGeocoder.parseNamedPlace(businesses(business(name="Flagler College FEC Residence Halls"),business(name="Flagler College FEC Dorms",lon=-81.32)),query,origin))
 }

 private fun modern(number: String="650",zip: String="32084",streetMatch: String="matched",confidence: String="high",lon: Double=-81.3)=JSONObject("""{"properties":{"feature_type":"address","context":{"address":{"address_number":"$number","street_name":"Main Street"},"postcode":{"name":"$zip"}},"match_code":{"address_number":"matched","street":"$streetMatch","confidence":"$confidence","postcode":"matched","place":"matched","region":"matched"}},"geometry":{"coordinates":[$lon,29.8]}}""")
 @Test fun modernAddressMatchingResolvesHighConfidenceExactHouseAndStreet() {
  assertEquals(origin,DasherGeocoder.parseV6(businesses(modern()),"650 Main St, St. Augustine, FL 32084",origin))
 }
 @Test fun modernMatchingRejectsWrongNumberStreetAndPostcode() {
  val query="650 Main St, St. Augustine, FL 32084"
  for (feature in listOf(modern(number="651"),modern(zip="32086"),modern(streetMatch="unmatched"),modern(confidence="low"))) assertNull(DasherGeocoder.parseV6(businesses(feature),query,origin))
 }
 @Test fun modernMatchingRejectsAmbiguousLocations() {
  assertNull(DasherGeocoder.parseV6(businesses(modern(),modern(lon=-81.32)),"650 Main St, St. Augustine, FL 32084",origin))
 }
 @Test fun modernMatchingPreservesVehicleAccess() {
  val feature=modern()
  feature.getJSONObject("properties").put("coordinates",JSONObject("""{"routable_points":[{"name":"default","longitude":-81.3001,"latitude":29.8}]}"""))
  assertEquals(-81.3001,DasherGeocoder.parseV6(businesses(feature),"650 Main St, St. Augustine, FL 32084",origin)!!.vehicleLon!!,.00001)
 }
}
