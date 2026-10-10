package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DasherAddressParserTest {
 @Test fun fullAddressKeepsCityZipAndUnit() {
  val result = DasherAddressParser.select(listOf("Deliver to","310 Summer Breeze Way, Apt 2B, Saint Augustine, FL 32084","Leave at door"))!!
  assertEquals("Entrega",result.stage)
  assertTrue(result.address.contains("FL 32084"))
  assertTrue(result.address.contains("Apt 2B"))
 }
 @Test fun ignoresUnacceptedOfferAndCompletedDelivery() {
  assertNull(DasherAddressParser.select(listOf("123 Main St","Accept")))
  assertNull(DasherAddressParser.select(listOf("Delivery complete","123 Main St")))
 }
 @Test fun ambiguousStopsDoNotPickFirst() {
  assertNull(DasherAddressParser.select(listOf("123 Main St","456 Oak Ave")))
 }
 @Test fun deliveryPhaseChoosesCustomerInsteadOfRestaurant() {
  val result = DasherAddressParser.select(listOf("Pickup address","123 Main St","Restaurant","Customer address","456 Oak Ave","Leave at door"))!!
  assertEquals("456 Oak Ave",result.address)
 }
 @Test fun pickupPhaseChoosesStore() {
  val result = DasherAddressParser.select(listOf("Pickup address","123 Main St","Directions","Customer address","456 Oak Ave","Confirm pickup"))!!
  assertEquals("123 Main St",result.address)
  assertEquals("Recogida",result.stage)
 }
 @Test fun splitCityLineIsRetained() {
  assertTrue(DasherAddressParser.select(listOf("123 Main St","Saint Augustine, FL 32084"))!!.address.contains("32084"))
 }
 @Test fun pricesAndPhoneNumbersAreNotAddresses() {
  assertNull(DasherAddressParser.select(listOf("$12.50","Call (904) 555-0191","2 miles","Arrived at store")))
 }
 @Test fun activePickupWinsOverFutureDeliveryInstructions() {
  val result = DasherAddressParser.select(listOf("Pickup address","123 Main St","Directions","Customer address","456 Oak Ave","Leave at door","Confirm pickup"))!!
  assertEquals("123 Main St",result.address)
  assertEquals("Recogida",result.stage)
 }
 @Test fun keepsStreetDirectionCountyRoadNumberAndSplitUnit() {
  val directional = DasherAddressParser.select(listOf("123 Main St N","Apt 2B","Saint Augustine, FL 32084"))!!
  assertTrue(directional.address.contains("St N"))
  assertTrue(directional.address.contains("Apt 2B"))
  assertEquals("123 County Road 210",DasherAddressParser.select(listOf("123 County Road 210"))!!.address)
 }
 @Test fun realDasherDeliveryWithPrefixStreet() {
  val result = DasherAddressParser.select(listOf("Entregar a Cliente", "antes de las 10:49 p. m.", "3237 Cll Cortez", "St. Augustine, FL 32086", "Dejar en la puerta", "Indicaciones"))!!
  assertEquals("Entrega",result.stage)
  assertEquals("3237 Cll Cortez, St. Augustine, FL 32086",result.address)
 }
 @Test fun realDasherPickupWithHighwayAndSuite() {
  val result = DasherAddressParser.select(listOf("Taco Bell", "La orden incluye", "Bebida", "1820 Us Highway 1 S # S", "St Augustine FL 32084-6017 United States", "Apt./ Suite: # S", "Llegué a la tienda"))!!
  assertEquals("Recogida",result.stage)
  assertTrue(result.address.startsWith("1820 Us Highway 1 S # S"))
  assertTrue(result.address.contains("32084-6017"))
 }
 @Test fun combinedAccessibleDeliveryLabel() {
  val result = DasherAddressParser.select(listOf("Entregar a Cliente", "3237 Cll Cortez\nSt. Augustine, FL 32086", "Indicaciones"))!!
  assertEquals("3237 Cll Cortez, St. Augustine, FL 32086",result.address)
 }
 @Test fun repeatedCardAndChildAddressAreOneDestination() {
  val result = DasherAddressParser.select(listOf("Entregar a Cliente", "3237 Cll Cortez, St. Augustine, FL 32086", "3237 Cll Cortez", "Indicaciones"))!!
  assertEquals("3237 Cll Cortez, St. Augustine, FL 32086",result.address)
 }
 @Test fun namedDeliveryFromActualDasherCard() {
  val result = DasherAddressParser.select(listOf("Entregar a Cliente", "antes de las 12:11 a. m.", "Llamar", "Mensaje", "Chase Bank", "Saint Augustine, FL 32084", "Reunirse afuera", "Continuar", "Indicaciones"))!!
  assertEquals("Chase Bank, Saint Augustine, FL 32084",result.address)
  assertEquals("Entrega",result.stage)
 }
 @Test fun combinedBusinessCardIsDeduplicated() {
  val result = DasherAddressParser.select(listOf("Deliver to Customer", "Chase Bank\nSaint Augustine, FL 32084", "Chase Bank, Saint Augustine, FL 32084"))!!
  assertEquals("Chase Bank, Saint Augustine, FL 32084",result.address)
 }
 @Test fun doesNotUseButtonsOrAmbiguousBusinessesAsDestinations() {
  assertNull(DasherAddressParser.select(listOf("Entregar a Cliente","Mensaje","Saint Augustine, FL 32084")))
  assertNull(DasherAddressParser.select(listOf("Entregar a Cliente","Chase Bank","Saint Augustine, FL 32084","Other Bank","Saint Augustine, FL 32084")))
  assertNull(DasherAddressParser.select(listOf("Chase Bank","Saint Augustine, FL 32084","Aceptar")))
 }

 @Test fun collegeHousingKeepsSpecificBuildingFromDasher() {
  val result = DasherAddressParser.select(listOf("Entregar a Kaila S", "Flagler College Fec Dorms", "Saint Augustine, FL 32084", "Código de acceso", "null", "Apt./Suite", "2nd", "Nombre del edificio", "FEC Tower- C", "Indicaciones"))!!
  assertEquals("Flagler College Fec Dorms · FEC Tower- C, Saint Augustine, FL 32084",result.address)
  assertEquals("Entrega",result.stage)
 }
 @Test fun ignoresMissingBuildingAndPreservesNumericAddress() {
  assertEquals("Chase Bank, Saint Augustine, FL 32084",DasherAddressParser.select(listOf("Deliver to Customer", "Chase Bank", "Saint Augustine, FL 32084", "Building name", "null"))!!.address)
  assertTrue(DasherAddressParser.select(listOf("Deliver to Customer", "123 Main St", "Saint Augustine, FL 32084", "Building name", "FEC Tower C"))!!.address.startsWith("123 Main St,"))
 }

 @Test fun pickupDeadlineRecognizesRestaurantEvenBeforeArrivalButton() {
  val result=DasherAddressParser.select(listOf("Para llevar antes de 12:17", "Taco Bell", "1820 Us Highway 1 S", "St. Augustine, FL 32084"))!!
  assertEquals("Recogida",result.stage)
  assertTrue(result.address.contains("32084"))
 }
 @Test fun apartmentFieldCannotBecomeAnotherDestination() {
  val result=DasherAddressParser.select(listOf("Entregar a Cliente", "650 Main St", "St. Augustine, FL 32084", "Apt./Suite", "650 West Main St"))!!
  assertEquals("650 Main St, St. Augustine, FL 32084",result.address)
 }
 @Test fun separateHouseNumberAndStreetNodesRetainCity() {
  val result=DasherAddressParser.select(listOf("Entregar a Cliente", "650", "Main St", "St. Augustine, FL 32084"))!!
  assertEquals("650 Main St, St. Augustine, FL 32084",result.address)
 }
}
