package com.hirolifecreative.floatingmap
import org.junit.Assert.*
import org.junit.Test
class PuckOrientationTest {
    @Test fun flatMapKeepsArrowFlatDespiteSavedTilt() {
        assertEquals(0f,PuckOrientation.viewTilt(50,0.0,60),0f)
    }
    @Test fun transitionFollowsActualCameraInsteadOfJumpingAtButtonPress() {
        assertEquals(0f,PuckOrientation.viewTilt(40,0.0,60),0f)
        assertEquals(20f,PuckOrientation.viewTilt(40,30.0,60),.001f)
        assertEquals(40f,PuckOrientation.viewTilt(40,60.0,60),.001f)
    }
    @Test fun forwardRotationDoesNotReverseAndRespectsSavedAngle() {
        assertEquals(25f,PuckOrientation.rotationX(25),0f)
        assertEquals(25f,PuckOrientation.viewTilt(25,75.0,60),0f)
        assertEquals(0f,PuckOrientation.viewTilt(0,60.0,60),0f)
    }
    @Test fun invalidAndFlatConfigurationsCannotProduceInvertedOrInvalidAngles() {
        assertEquals(0f,PuckOrientation.viewTilt(40,Double.NaN,60),0f)
        assertEquals(0f,PuckOrientation.viewTilt(40,60.0,0),0f)
        assertEquals(0f,PuckOrientation.viewTilt(-10,60.0,60),0f)
        assertEquals(60f,PuckOrientation.viewTilt(90,60.0,60),0f)
    }
}
