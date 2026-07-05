package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGestureTest {

    @Test
    fun encodeCustomGestureRequiresEnoughTravelAndDirectionChanges() {
        val short = listOf(
            CustomGesturePoint(0f, 0f),
            CustomGesturePoint(8f, 0f),
            CustomGesturePoint(14f, 0f),
            CustomGesturePoint(20f, 0f),
        )

        assertEquals("", encodeCustomGesture(short))
    }

    @Test
    fun encodeCustomGestureCapturesShapeDirections() {
        val lShape = listOf(
            CustomGesturePoint(0f, 0f),
            CustomGesturePoint(80f, 0f),
            CustomGesturePoint(160f, 0f),
            CustomGesturePoint(160f, 80f),
            CustomGesturePoint(160f, 160f),
        )

        assertEquals("02", encodeCustomGesture(lShape))
    }

    @Test
    fun customGesturePatternMatchingAllowsSmallRecordingVariance() {
        assertTrue(customGesturePatternMatches("0246", "024"))
        assertFalse(customGesturePatternMatches("0246", "2460"))
    }
}
