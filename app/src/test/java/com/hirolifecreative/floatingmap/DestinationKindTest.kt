package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DestinationKindTest {
 @Test fun stageDeterminesColorInsteadOfCustomerOrRestaurantName() {
  assertEquals(DestinationKind.PICKUP,DestinationKind.fromName("Dasher · Recogida: Taco Bell"))
  assertEquals(DestinationKind.DELIVERY,DestinationKind.fromName("Dasher · Entrega: Chase Bank"))
  assertEquals(DestinationKind.OTHER,DestinationKind.fromName("Recogida Avenue"))
 }
 @Test fun savedRoleSurvivesRestartAndOldSessionsInferDasherStage() {
  assertEquals(DestinationKind.PICKUP,DestinationKind.restore("PICKUP","Taco Bell"))
  assertEquals(DestinationKind.DELIVERY,DestinationKind.restore("","Dasher · Entrega: 123 Main St"))
  assertEquals(DestinationKind.OTHER,DestinationKind.restore("unknown","123 Main St"))
 }
}
