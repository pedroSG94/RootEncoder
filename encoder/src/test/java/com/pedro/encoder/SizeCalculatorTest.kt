package com.pedro.encoder

import android.util.Pair
import android.graphics.Point
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.SizeCalculator
import junit.framework.TestCase.assertEquals
import org.junit.Test


/**
 * Possible cases:
 *
 * stream to preview (Adjust and Fill)
 * 1:1 to 1:1
 * 1:1 to 16:9
 * 1:1 to 9:16
 *
 * 16:9 to 16:9
 * 16:9 to 1:1
 * 16:9 to 9:16
 *
 * 9:16 to 9:16
 * 9:16 to 16:9
 * 9:16 to 1:1
 */
class SizeCalculatorTest {

    @Test
    fun testAdjust1_1toX() {
        val to1_1 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 100, 100, 100, 100)
        assertEquals(Pair(Point(0, 0), Point(100, 100)), to1_1)

        val to16_9 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 160, 90, 100, 100)
        assertEquals(Pair(Point(35, 0), Point(90, 90)), to16_9)

        val to9_16 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 90, 160, 100, 100)
        assertEquals(Pair(Point(0, 35), Point(90, 90)), to9_16)
    }

    @Test
    fun testAdjust16_9toX() {
        val to1_1 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 100, 100, 100, 100)
        assertEquals(Pair(Point(0, 0), Point(100, 100)), to1_1)

        val to16_9 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 160, 90, 100, 100)
        assertEquals(Pair(Point(35, 0), Point(90, 90)), to16_9)

        val to9_16 = SizeCalculator.getViewport(AspectRatioMode.Adjust, 90, 160, 100, 100)
        assertEquals(Pair(Point(0, 35), Point(90, 90)), to9_16)
    }

    @Test
    fun testAdjust9_16toX() {

    }

    @Test
    fun testFill1_1toX() {

    }

    @Test
    fun testFill16_9toX() {

    }

    @Test
    fun testFill9_16toX() {

    }
}