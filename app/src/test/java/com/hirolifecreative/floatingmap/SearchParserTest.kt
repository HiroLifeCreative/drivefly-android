package com.hirolifecreative.floatingmap

import org.junit.Assert.*
import org.junit.Test

class SearchParserTest {
    @Test fun acceptsTypicalNominatimResponseAndAnyFieldOrder() {
        val first = SearchParser.parse("""[{"place_id":1,"lat":"19.43","lon":"-99.13","display_name":"Centro, Ciudad de México, México"}]""")
        val reordered = SearchParser.parse("""[{"display_name":"Centro, Ciudad de México, México","lon":"-99.13","lat":"19.43","place_id":1}]""")
        assertEquals(first, reordered)
        assertEquals("Centro, Ciudad de México", first.single().name)
        assertEquals(-99.13, first.single().longitude, 0.00001)
    }
    @Test fun skipsInvalidCoordinatesWithoutDiscardingValidPlaces() {
        val results = SearchParser.parse("""[{"lat":"NaN","lon":"0","display_name":"Bad"},{"lat":"91","lon":"0","display_name":"Bad"},{"lat":"0","lon":"0","display_name":"Valid"}]""")
        assertEquals(1, results.size)
        assertEquals("Valid", results.single().name)
    }
    @Test fun emptyResponseHasNoResults() { assertTrue(SearchParser.parse("[]").isEmpty()) }
}
