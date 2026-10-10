package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class NavigationColorConfigTest {
    @Test fun defaultsKeepBlueRouteAndDestinationRoles() {
        val c=NavigationColorConfig()
        assertEquals("#007AFF",c.route);assertEquals("#16A765",c.pickup);assertEquals("#FF3B30",c.delivery)
    }
    @Test fun customColorsNormalizeAndPersistIndependently() {
        val c=NavigationColorConfig().withColor("route"," ab12ef ").withColor("pickup","#102030")
        assertEquals("#AB12EF",c.route);assertEquals("#102030",c.pickup);assertEquals("#FF3B30",c.delivery)
        assertEquals(c,NavigationColorConfig.parse(c.json()))
    }
    @Test fun malformedAndTransparentColorsFallBackIndividually() {
        val c=NavigationColorConfig.parse(JSONObject().put("route","#00000000").put("pickup","green").put("delivery","#123456"))
        assertEquals("#007AFF",c.route);assertEquals("#16A765",c.pickup);assertEquals("#123456",c.delivery)
        assertNull(NavigationColorConfig.normalize("#abc"));assertNull(NavigationColorConfig.normalize("#ZZZZZZ"))
    }
    @Test fun routeCasingFollowsSelectedHueWithoutWhiteBorder() {
        assertEquals("#9E0000",NavigationColorConfig.casing("#FF0000"))
        assertEquals("#00009E",NavigationColorConfig.casing("#0000FF"))
        assertEquals("#000000",NavigationColorConfig.casing("#000000"))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidCustomValueCannotBeSaved() {
        NavigationColorConfig().withColor("route","#F00")
    }
}
