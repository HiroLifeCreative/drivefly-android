package com.hirolifecreative.floatingmap
import org.junit.Test
import org.junit.Assert.*
class ThemePaletteTest {
 @Test fun preservesWhiteActionTextAndSemanticColors() {
  assertEquals(-1,ThemePalette.text(-1,true))
  assertEquals(0xff34c759.toInt(),ThemePalette.text(0xff34c759.toInt(),true))
  assertEquals(0xffff3b30.toInt(),ThemePalette.background(0xffff3b30.toInt(),true))
 }
 @Test fun darkTextHasReadableContrastOnDarkCards() {
  fun luminance(c: Int): Double {
   fun channel(v: Int): Double { val x=v/255.0;return if(x<=.04045)x/12.92 else Math.pow((x+.055)/1.055,2.4) }
   return .2126*channel(c shr 16 and 255)+.7152*channel(c shr 8 and 255)+.0722*channel(c and 255)
  }
  val card=ThemePalette.background(-1,true)
  for (ink in listOf(0xff1c1c1e.toInt(),0xff1c2635.toInt(),0xff636366.toInt())) {
   assertTrue((luminance(ThemePalette.text(ink,true))+.05)/(luminance(card)+.05)>=4.5)
  }
 }
}
