package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DeliverySpeechTest {
 @Test fun extractsStoreFromExplicitOfferAndAcceptedPickup() {
  assertEquals("McDonald's",DeliverySpeech.merchant(listOf("Nuevo pedido", "Recoger en McDonald's", "$7.50", "3.2 mi", "Aceptar", "Rechazar"),true))
  assertEquals("Taco Bell",DeliverySpeech.merchant(listOf("Para llevar antes de 12:17", "Taco Bell", "La orden incluye", "Bebida", "1820 US Highway 1 S", "Llegué a la tienda")))
  assertEquals("McDonald's",DeliverySpeech.merchant(listOf("New order: McDonald's"),true))
 }
 @Test fun doesNotSpeakUiLabelsPricesOrAmbiguousStoresAsNames() {
  assertNull(DeliverySpeech.merchant(listOf("Aceptar", "Rechazar", "$12.50", "3.2 mi"),true))
  assertNull(DeliverySpeech.merchant(listOf("Recoger en Taco Bell", "Recoger en McDonald's"),true))
  assertNull(DeliverySpeech.merchant(listOf("Restaurant address", "123 Main St", "St. Augustine, FL 32084"),true))
 }
 @Test fun readsApartmentButNotCodesOrRepeatedStreetAddresses() {
  assertEquals("2B",DeliverySpeech.unit(listOf("Código de acceso", "1234", "Apt./Suite", "2B")))
  assertEquals("",DeliverySpeech.unit(listOf("Apt./Suite", "650 West Main St")))
  assertEquals("",DeliverySpeech.unit(listOf("Apt./Suite", "null")))
 }
 private fun route(type: String="arrive",side: String="right")="""{"routes":[{"legs":[{"steps":[{"maneuver":{"type":"$type","modifier":"$side"}}]}]}]}"""
 @Test fun conciseClientArrivalUsesHouseApartmentAndConfirmedSide() {
  assertEquals("Llegaste. Casa 327, a la derecha. Apartamento 2B.",DeliverySpeech.arrival("Dasher · Entrega: 327 Main St, Saint Augustine, FL 32084",DestinationKind.DELIVERY,"","2B",route()))
  assertEquals("Llegaste. Casa 327, a la izquierda.",DeliverySpeech.arrival("327 Main St",DestinationKind.DELIVERY,"","",route(side="left")))
 }
 @Test fun absentSideAndNonArrivalTurnCannotInventDestinationSide() {
  assertEquals("Llegaste. Casa 327.",DeliverySpeech.arrival("327 Main St",DestinationKind.DELIVERY,"","",null))
  assertEquals("Llegaste. Casa 327.",DeliverySpeech.arrival("327 Main St",DestinationKind.DELIVERY,"","",route(type="turn")))
  assertEquals("Llegaste a tu destino.",DeliverySpeech.arrival("Punto en el mapa",DestinationKind.OTHER,"","",null))
 }
 @Test fun pickupUsesStoreAndInlineApartmentIsPreserved() {
  assertEquals("Llegaste a McDonald's.",DeliverySpeech.arrival("123 Main St",DestinationKind.PICKUP,"McDonald's","",null))
  assertEquals("Llegaste. Casa 327. Apartamento 2B.",DeliverySpeech.arrival("327 Main St, Apt 2B, FL 32084",DestinationKind.DELIVERY,"","",null))
 }
 @Test fun repeatedOfferAndAcceptedPickupOnlyAnnounceOnce() {
  val gate=OrderAnnouncementGate()
  assertEquals("Nuevo pedido. Recoge en McDonald's.",gate.observe(true,"offer1","McDonald's",false,0))
  assertNull(gate.observe(true,"offer1","McDonald's",false,2000))
  assertNull(gate.observe(false,"","McDonald's",true,4000))
  assertNull(gate.observe(false,"","McDonald's",true,130000))
 }
 @Test fun anotherOfferAtSameStoreCanAnnounceAfterScreenChanges() {
  val gate=OrderAnnouncementGate()
  assertNotNull(gate.observe(true,"same","Taco Bell",false,0))
  assertNull(gate.observe(false,"","",false,2000))
  assertNotNull(gate.observe(true,"same","Taco Bell",false,12000))
 }
 @Test fun notificationAndScreenDoNotDoubleSpeakSameStoreInSeconds() {
  val gate=OrderAnnouncementGate()
  assertNotNull(gate.observe(true,"notification","Taco Bell",false,0))
  assertNull(gate.observe(true,"screen","Taco Bell",false,1000))
  assertNotNull(gate.observe(true,"another","Wingstop",false,3000))
 }
 @Test fun acceptedPickupWithoutOfferStillAnnouncesStoreOnce() {
  val gate=OrderAnnouncementGate()
  assertNotNull(gate.observe(false,"","Wingstop",true,0))
  assertNull(gate.observe(false,"","Wingstop",true,2000))
 }
 @Test fun destinationMetadataKeepsApartmentOutsideGeocodingQuery() {
  val d=DasherAddressParser.select(listOf("Entregar a Cliente", "327 Main St", "St. Augustine, FL 32084", "Apt./Suite", "2B"))!!
  assertEquals("2B",d.unit)
  assertEquals("327 Main St, St. Augustine, FL 32084",d.address)
 }
 @Test fun brandNamesWithNumbersOrSpanishMiAreNotUiMetrics() {
  assertEquals("3Natives",DeliverySpeech.merchant(listOf("Recoger en 3Natives"),true))
  assertEquals("Mi Pueblo",DeliverySpeech.merchant(listOf("Recoger en Mi Pueblo"),true))
  assertEquals("7-Eleven",DeliverySpeech.merchant(listOf("Recoger en 7-Eleven"),true))
 }
 @Test fun acceptedScreenshotUsesPickupHeadingInsteadOfDessert() {
  val screen=listOf("Para llevar antes de 1:50", "$5.20", "esta oferta", "42,85 US$", "este dash", "Saint Augustine", "Recoger desde", "McDonald's", "La orden incluye", "Postre", "1106 N Ponce De Leon Blvd", "St Augustine FL 32084-3178, United States", "Indicaciones")
  assertEquals("McDonald's",DeliverySpeech.merchant(screen))
  assertEquals("Nuevo pedido. Recoge en McDonald's.",DeliverySpeech.order(DeliverySpeech.merchant(screen)!!))
 }
 @Test fun pickupLabelsCanBeCombinedAndEnglish() {
  assertEquals("McDonald's",DeliverySpeech.merchant(listOf("Recoger desde McDonald's", "Postre")))
  assertEquals("Taco Bell",DeliverySpeech.merchant(listOf("Pick up from", "Taco Bell", "Order includes", "Dessert")))
 }
 @Test fun missingMerchantNeverFallsBackToOrderContents() {
  for(item in listOf("Postre","Postres","Dessert","Desserts","Bebida","Bebidas","Alcohol","Pizza")) {
   assertNull(DeliverySpeech.merchant(listOf("Recoger desde", "La orden incluye", item, "1106 N Ponce De Leon Blvd")))
  }
 }
}
