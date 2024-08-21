package com.pedro.encoder

import com.pedro.encoder.utils.ViewPort
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.SizeCalculator
import junit.framework.TestCase.assertEquals
import org.junit.Test


class SizeCalculatorTest {

    @Test
    fun `calculate viewport preview cases with adjust mode`() {
        //higher aspect ratio than preview
        val result = SizeCalculator.calculateViewPort(AspectRatioMode.Adjust, 100, 100, 160, 90)
        assertEquals(ViewPort(0, 22, 100, 56), result)
        //lower aspect ratio than preview
        val result2 = SizeCalculator.calculateViewPort(AspectRatioMode.Adjust, 100, 100, 90, 160)
        assertEquals(ViewPort(22, 0, 56, 100), result2)
        //equal
        val result3 = SizeCalculator.calculateViewPort(AspectRatioMode.Adjust, 100, 100, 100, 100)
        assertEquals(ViewPort(0, 0, 100, 100), result3)
    }

    @Test
    fun `calculate viewport preview cases with fill mode`() {
        //higher aspect ratio than preview
        val result = SizeCalculator.calculateViewPort(AspectRatioMode.Fill, 100, 100, 160, 90)
        assertEquals(ViewPort(-38, 0, 177, 100), result)
        //lower aspect ratio than preview
        val result2 = SizeCalculator.calculateViewPort(AspectRatioMode.Fill, 100, 100, 90, 160)
        assertEquals(ViewPort(0, -38, 100, 177), result2)
        //equal
        val result3 = SizeCalculator.calculateViewPort(AspectRatioMode.Fill, 100, 100, 160, 90)
        assertEquals(ViewPort(-38, 0, 177, 100), result3)
    }

    @Test
    fun `calculate viewport preview cases with none mode`() {
        val result = SizeCalculator.calculateViewPort(AspectRatioMode.NONE, 100, 100, 160, 90)
        assertEquals(ViewPort(0, 0, 100, 100), result)
        val result2 = SizeCalculator.calculateViewPort(AspectRatioMode.NONE, 100, 100, 90, 160)
        assertEquals(ViewPort(0, 0, 100, 100), result2)
        val result3 = SizeCalculator.calculateViewPort(AspectRatioMode.NONE, 100, 100, 100, 100)
        assertEquals(ViewPort(0, 0, 100, 100), result3)
    }

    @Test
    fun `calculate flipH and flipV cases`() {
        val result = SizeCalculator.calculateFlip(true, true)
        assertEquals(Pair(-1f, -1f), result)
        val result2 = SizeCalculator.calculateFlip(false, false)
        assertEquals(Pair(1f, 1f), result2)
        val result3 = SizeCalculator.calculateFlip(true, false)
        assertEquals(Pair(-1f, 1f), result3)
        val result4 = SizeCalculator.calculateFlip(false, true)
        assertEquals(Pair(1f, -1f), result4)
    }

    @Test
    fun `calculate viewport encoder cases`() {
        //aspect ratio factor > 1f
        val result = SizeCalculator.calculateViewPortEncoder(160, 90, true)
        assertEquals(ViewPort(55, 0, 50, 90), result)
        val result2 = SizeCalculator.calculateViewPortEncoder(160, 90, false)
        assertEquals(ViewPort(0, 0, 160, 90), result2)
        //aspect ratio factor < 1f
        val result3 = SizeCalculator.calculateViewPortEncoder(90, 160, true)
        assertEquals(ViewPort(0, 0, 90, 160), result3)
        val result4 = SizeCalculator.calculateViewPortEncoder(90, 160, false)
        assertEquals(ViewPort(0, 55, 90, 50), result4)
        //aspect ratio factor = 1f
        val result5 = SizeCalculator.calculateViewPortEncoder(100, 100, true)
        assertEquals(ViewPort(0, 0, 100, 100), result5)
        val result6 = SizeCalculator.calculateViewPortEncoder(100, 100, false)
        assertEquals(ViewPort(0, 0, 100, 100), result6)
    }
}