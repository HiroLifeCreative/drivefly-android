package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
class NavigationZoomScaleTest {
    @Test fun savedSizeIsUnchangedAtBaseZoom() { assertEquals(1.0,NavigationZoomScale.factor(CameraConfig.BASE_ZOOM),1e-9) }
    @Test fun zoomingInIncreasesSizeAndZoomingOutReducesIt() {
        var previous=0.0
        for(z in 100..220) { val factor=NavigationZoomScale.factor(z/10.0);assertTrue(factor>=previous);previous=factor }
        assertTrue(NavigationZoomScale.factor(14.0)<1);assertTrue(NavigationZoomScale.factor(19.0)>1)
    }
    @Test fun extremesStayVisibleAndBounded() {
        assertEquals(.4,NavigationZoomScale.factor(-100.0),0.0)
        assertEquals(1.7,NavigationZoomScale.factor(100.0),0.0)
        assertEquals(1.0,NavigationZoomScale.factor(Double.NaN),0.0)
    }
    @Test fun interpolationDoesNotJumpAtStops() {
        for((zoom,scale) in NavigationZoomScale.stops) {
            assertEquals(scale,NavigationZoomScale.factor(zoom-1e-6),1e-5)
            assertEquals(scale,NavigationZoomScale.factor(zoom+1e-6),1e-5)
        }
    }
    @Test fun mapExpressionsMatchOverlayScaleAndPreserveOffsets() {
        val base=15.0;val expression=JSONArray(NavigationZoomScale.expression(base))
        for(i in NavigationZoomScale.stops.indices) {
            val zoom=expression.getDouble(3+i*2)
            assertEquals(base*NavigationZoomScale.factor(zoom),expression.getDouble(4+i*2),1e-9)
        }
        assertTrue(JSONArray(NavigationZoomScale.expression(-5.5)).getDouble(4)<0)
    }
    @Test fun modelExpressionScalesAllAxesAndRespectsUserSize() {
        val expression=JSONArray(NavigationZoomScale.modelExpression(48.0))
        for(i in NavigationZoomScale.stops.indices) {
            val literal=expression.getJSONArray(4+i*2);assertEquals("literal",literal.getString(0))
            val axes=literal.getJSONArray(1);assertEquals(3,axes.length())
            for(j in 0..2) assertEquals(48*NavigationZoomScale.stops[i].second,axes.getDouble(j),1e-9)
        }
    }
}
