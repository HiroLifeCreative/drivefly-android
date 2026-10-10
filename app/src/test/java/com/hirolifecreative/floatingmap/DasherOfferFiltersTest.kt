package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class DasherOfferFiltersTest {
 private val filters=OfferFilterConfig()
 @Test fun userExampleNeverPasses() {
  val m=DasherOfferFilters.metrics(listOf("Aceptar","$4.00","10 mi"));assertEquals(4.0,m.pay!!,0.001);assertEquals(10.0,m.miles!!,0.001)
  assertNotNull(DasherOfferFilters.reason(filters,m))
 }
 @Test fun combinedThresholdsAreInclusive() {
  assertNull(DasherOfferFilters.reason(filters,OfferMetrics(6.0,4.0)))
  assertNull(DasherOfferFilters.reason(filters,OfferMetrics(15.0,10.0)))
  assertNotNull(DasherOfferFilters.reason(filters,OfferMetrics(15.0,10.01)))
  assertNotNull(DasherOfferFilters.reason(filters,OfferMetrics(6.0,4.01)))
 }
 @Test fun highPaymentStillFailsDistanceAndHighDistanceFailsRate() {
  assertNotNull(DasherOfferFilters.reason(filters,OfferMetrics(100.0,11.0)))
  assertNotNull(DasherOfferFilters.reason(filters,OfferMetrics(10.0,10.0)))
 }
 @Test fun readsSpanishCurrencyAndDecimalComma() {
  val m=DasherOfferFilters.metrics(listOf("8,50 US$","3,2 millas"));assertEquals(8.5,m.pay!!,0.001);assertEquals(3.2,m.miles!!,0.001)
 }
 @Test fun convertsKilometersToMiles() {
  val m=DasherOfferFilters.metrics(listOf("USD 12.00","16.09344 km"));assertEquals(10.0,m.miles!!,0.00001)
 }
 @Test fun duplicateAccessibilityLabelsDoNotCreateAmbiguity() {
  val m=DasherOfferFilters.metrics(listOf("$8.00","$8.00","3 mi","3 miles"));assertEquals(8.0,m.pay!!,0.001);assertEquals(3.0,m.miles!!,0.001)
 }
 @Test fun ambiguousValuesRequireManualReview() {
  val m=DasherOfferFilters.metrics(listOf("$4.00","$10.00","2 mi","8 mi"));assertNull(m.pay);assertNull(m.miles);assertNotNull(DasherOfferFilters.reason(filters,m))
 }
 @Test fun explicitGuaranteedPayAndTotalDistanceTakePriority() {
  val m=DasherOfferFilters.metrics(listOf("$3.00","Guaranteed","$9.00","2 mi pickup","3 mi delivery","5 mi total"));assertEquals(9.0,m.pay!!,0.001);assertEquals(5.0,m.miles!!,0.001)
 }
 @Test fun bonusAndRateAreNotOfferPayment() {
  val m=DasherOfferFilters.metrics(listOf("$6.00 guaranteed","+$2.00 bonus","$2 per mile","3 mi"));assertEquals(6.0,m.pay!!,0.001)
 }
 @Test fun missingOrInvalidRequiredDataNeverPasses() {
  for(m in listOf(OfferMetrics(null,2.0),OfferMetrics(10.0,null),OfferMetrics(10.0,0.0),OfferMetrics(Double.NaN,2.0),OfferMetrics(10.0,Double.POSITIVE_INFINITY))) assertNotNull(DasherOfferFilters.reason(filters,m))
 }
 @Test fun disablingFiltersPreservesAcceptAllAndIndividualSwitchesWork() {
  assertNull(DasherOfferFilters.reason(filters.copy(enabled=false),OfferMetrics(null,null)))
  assertNull(DasherOfferFilters.reason(filters.copy(maximumMilesEnabled=false,minimumRateEnabled=false),OfferMetrics(6.0,null)))
  assertNull(DasherOfferFilters.reason(filters.copy(minimumPayEnabled=false,minimumRateEnabled=false),OfferMetrics(null,8.0)))
 }
 @Test fun positivePayWithZeroDistanceDoesNotProduceInfiniteProfit() {
  assertNotNull(DasherOfferFilters.reason(filters.copy(minimumPayEnabled=false,maximumMilesEnabled=false),OfferMetrics(20.0,0.0)))
 }
}
