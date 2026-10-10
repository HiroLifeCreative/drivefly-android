package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class BusinessHoursTest {
 @Test fun usesBusinessTimezoneAndClosingBoundary() {
  assertEquals(true,BusinessHours.openNow("Mo-Fr 09:00-17:00","America/New_York",Instant.parse("2026-10-07T16:00:00Z")))
  assertEquals(false,BusinessHours.openNow("Mo-Fr 09:00-17:00","America/New_York",Instant.parse("2026-10-07T21:00:00Z")))
 }
 @Test fun overnightIncludesFollowingDay() {
  assertEquals(true,BusinessHours.openNow("Fr 20:00-02:00","UTC",Instant.parse("2026-10-10T01:00:00Z")))
  assertEquals(false,BusinessHours.openNow("Fr 20:00-02:00","UTC",Instant.parse("2026-10-10T02:00:00Z")))
 }
 @Test fun doesNotGuessOnUnsupportedRulesOrMissingTimezone() {
  assertNull(BusinessHours.openNow("Mo-Fr 09:00-17:00; PH off","UTC"))
  assertNull(BusinessHours.openNow("Mo-Fr 09:00-17:00",null))
  assertEquals(true,BusinessHours.openNow("24/7",null))
 }
 @Test fun thirtyMinutesWarnsButThirtyOneDoesNot() {
  assertEquals(BusinessHours.State.CLOSING,BusinessHours.status("We 09:00-17:00","UTC",instant=Instant.parse("2026-10-07T16:30:00Z")).state)
  assertEquals(BusinessHours.State.OPEN,BusinessHours.status("We 09:00-17:00","UTC",instant=Instant.parse("2026-10-07T16:29:00Z")).state)
  assertEquals(BusinessHours.State.CLOSED,BusinessHours.status("We 09:00-17:00","UTC",instant=Instant.parse("2026-10-07T17:00:00Z")).state)
 }
 @Test fun overnightClosingAndContinuousShifts() {
  assertEquals(BusinessHours.State.CLOSING,BusinessHours.status("Fr 20:00-02:00","UTC",instant=Instant.parse("2026-10-10T01:40:00Z")).state)
  assertEquals(BusinessHours.State.OPEN,BusinessHours.status("We 09:00-12:00,12:00-17:00","UTC",instant=Instant.parse("2026-10-07T11:50:00Z")).state)
 }
 @Test fun unavailableDataIsNeverMarkedClosed() {
  assertEquals(BusinessHours.State.UNKNOWN,BusinessHours.status(null,"UTC").state)
  assertEquals(BusinessHours.State.CLOSED,BusinessHours.status(null,null,true).state)
 }
 @Test fun publishedHoursWithMissingTimezoneAreNotCalledMissingHours() {
  val status = BusinessHours.status("Mo-Su 10:00-22:00",null)
  assertEquals(BusinessHours.State.UNKNOWN,status.state)
  assertTrue(status.label.contains("Horario publicado"))
  assertFalse(status.label.contains("Sin horario"))
 }
}
