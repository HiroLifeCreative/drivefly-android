package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class NamedPlaceMatcherTest {
 @Test fun queriesSpecificBuildingAndHousingAliases() {
  val q=NamedPlaceMatcher.queries("Flagler College Fec Dorms · FEC Tower- C, Saint Augustine, FL 32084")
  assertEquals("FEC Tower- C, Saint Augustine, FL 32084",q.first())
  assertTrue(q.any { it.contains("Residence Halls") })
  assertEquals(3,q.size)
 }
 @Test fun normalizationIgnoresAccentsPunctuationAndHousingTerminology() {
  assertTrue(NamedPlaceMatcher.matches("St. José School","Saint Jose School"))
  assertTrue(NamedPlaceMatcher.matches("FEC Dormitory","FEC Student Housing"))
  assertFalse(NamedPlaceMatcher.matches("School","Saint Jose School"))
 }
 @Test fun hotelAffiliationDoesNotBreakNameOrSearchIdentity() {
  val address="Sebastian Hotel, A Member Of Radisson Individuals, Saint Augustine, FL 32084"
  val parts=DasherAddressParser.namedPlaceParts(address)!!
  assertEquals("Sebastian Hotel, A Member Of Radisson Individuals",parts.first)
  assertEquals("Saint Augustine, FL 32084",parts.second)
  assertTrue(NamedPlaceMatcher.queries(address).contains("Sebastian Hotel, Saint Augustine, FL 32084"))
  assertTrue(NamedPlaceMatcher.matches(parts.first,"Sebastian Hotel"))
  assertFalse(NamedPlaceMatcher.matches(parts.first,"Radisson Hotel"))
  assertFalse(NamedPlaceMatcher.matches(parts.first,"Sebastian Resort"))
 }
 @Test fun hotelScreenshotAndWrappedNameBecomeDeliveryDestination() {
  val name="Sebastian Hotel, A Member Of Radisson Individuals"
  val d=DasherAddressParser.select(listOf("Entregar a Gavin T",name,"Saint Augustine, FL 32084","Nombre del edificio",name,"Dejar en el vestíbulo","Denny's (7047)","Indicaciones"))!!
  assertEquals("$name, Saint Augustine, FL 32084",d.address)
  assertEquals("Entrega",d.stage)
  val wrapped=DasherAddressParser.select(listOf("Entregar a Gavin T","Sebastian Hotel, A Member","Of Radisson Individuals","Saint Augustine, FL 32084","Indicaciones"))!!
  assertEquals(d.address,wrapped.address)
 }
 @Test fun hotelCityAndZipStillRequired() {
  assertNull(DasherAddressParser.namedPlaceParts("Sebastian Hotel, A Member Of Radisson Individuals"))
  assertNull(DasherAddressParser.namedPlaceParts("Sebastian Hotel, Saint Augustine"))
 }
 @Test fun collapsedHotelScreenStillIdentifiesDeliveryFromLobbyInstruction() {
  val d=DasherAddressParser.select(listOf("Gavin T","Sebastian Hotel, A Member Of Radisson Individuals","Saint Augustine, FL 32084","Dejar en el vestíbulo","Denny's (7047)","Indicaciones"))!!
  assertEquals("Entrega",d.stage)
  assertTrue(d.address.startsWith("Sebastian Hotel,"))
 }
}
