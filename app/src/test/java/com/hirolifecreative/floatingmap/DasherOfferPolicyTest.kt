package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DasherOfferPolicyTest {
 @Test fun recognizesBilingualOfferActionsWithoutOtherButtons() {
  listOf("Aceptar","Accept","Accept offer","Aceptar pedido","Aceptar (25)").forEach { assertTrue(it,DasherOfferPolicy.isAccept(it)) }
  listOf("No aceptar","Aceptar permisos","Aceptar y continuar","Aceptado","Confirmar recogida","Continuar").forEach { assertFalse(it,DasherOfferPolicy.isAccept(it)) }
 }
 @Test fun requiresOfferContextAndAcceptAction() {
  assertTrue(DasherOfferPolicy.isOffer(listOf("Aceptar","Rechazar","Taco Bell")))
  assertTrue(DasherOfferPolicy.isOffer(listOf("Accept","$8.50","3.2 mi")))
  assertTrue(DasherOfferPolicy.isOffer(listOf("Aceptar","8,50 US$","3,2 millas")))
  assertFalse(DasherOfferPolicy.isOffer(listOf("Aceptar","Aviso de permisos")))
  assertFalse(DasherOfferPolicy.isOffer(listOf("Continuar","$8.50","3 mi")))
 }
 @Test fun disabledModeNeverClicks() {
  val latch=DasherOfferLatch();assertFalse(latch.mayClick(0,false,true))
 }
 @Test fun successfulClickDoesNotRepeatWhileOfferRemains() {
  val latch=DasherOfferLatch();assertTrue(latch.mayClick(0,true,true));latch.recordClick(0,true)
  assertFalse(latch.mayClick(3000,true,true));assertFalse(latch.mayClick(60000,true,true))
 }
 @Test fun transientDisappearanceDoesNotPermitDuplicateButNextOfferDoes() {
  val latch=DasherOfferLatch();latch.recordClick(0,true)
  assertFalse(latch.mayClick(1000,true,false));assertFalse(latch.mayClick(2000,true,true))
  assertFalse(latch.mayClick(3000,true,false));assertFalse(latch.mayClick(7000,true,false));assertTrue(latch.mayClick(8000,true,true))
 }
 @Test fun failedClicksHaveBackoffAndMaximumAttempts() {
  val latch=DasherOfferLatch()
  for(t in listOf(0L,3000L,6000L)) { assertTrue(latch.mayClick(t,true,true));latch.recordClick(t,false);assertFalse(latch.mayClick(t+100,true,true)) }
  assertFalse(latch.mayClick(9000,true,true))
 }
 @Test fun disablingClearsPendingAttempt() {
  val latch=DasherOfferLatch();latch.recordClick(0,true);assertFalse(latch.mayClick(1000,false,true));assertTrue(latch.mayClick(2000,true,true))
 }
 @Test fun offerAddressCannotStartRouteBeforeAcceptance() {
  assertNull(DasherAddressParser.select(listOf("123 Main St","Aceptar oferta")))
  assertNull(DasherAddressParser.select(listOf("123 Main St","Accept (20)")))
  assertEquals("Recogida",DasherAddressParser.select(listOf("123 Main St","Llegué a la tienda"))!!.stage)
 }
}
