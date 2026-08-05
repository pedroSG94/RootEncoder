package com.pedro.common

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class TimestampQuantizerTest {

    @Test
    fun testJitterRemoved() {
        val quantizer = TimestampQuantizer()
        //clock timestamps of a 30fps source with the jitter of a render thread
        val timestamps = listOf(
            0L, 30000L, 71000L, 95000L, 135000L, 160000L, 202000L, 229000L,
            270000L, 296000L, 335000L, 364000L, 399000L, 431000L, 468000L
        )
        val result = timestamps.map { quantizer.quantize(it, 30) }
        assertEquals(
            listOf(
                0L, 33333L, 66666L, 100000L, 133333L, 166666L, 200000L, 233333L,
                266666L, 300000L, 333333L, 366666L, 400000L, 433333L, 466666L
            ),
            result
        )
    }

    @Test
    fun testSlowerSourceSkipSlots() {
        val quantizer = TimestampQuantizer()
        //source producing a frame every 2 slots (15fps in a 30fps grid)
        val timestamps = listOf(0L, 68000L, 130000L, 202000L, 264000L)
        val result = timestamps.map { quantizer.quantize(it, 30) }
        assertEquals(listOf(0L, 66666L, 133333L, 200000L, 266666L), result)
    }

    @Test
    fun testRateThatDoesNotFitGridNotModified() {
        val quantizer = TimestampQuantizer()
        //24fps source in a 30fps grid, a grid would produce a worse result
        val timestamps = listOf(0L, 41000L, 84000L, 125000L, 166000L, 209000L)
        val result = timestamps.map { quantizer.quantize(it, 30) }
        assertEquals(timestamps, result)
    }

    @Test
    fun testIrregularSourceNotModified() {
        val quantizer = TimestampQuantizer()
        //a source that isn't running at the expected fps is returned as is
        val timestamps = listOf(0L, 45000L, 100000L, 145000L, 200000L, 245000L)
        val result = timestamps.map { quantizer.quantize(it, 30) }
        assertEquals(timestamps, result)
    }

    @Test
    fun testFasterSourceUsesHalfGrid() {
        val quantizer = TimestampQuantizer()
        //60fps source in a 30fps grid dropping frames in a irregular way, like a video file
        val timestamps = listOf(
            0L, 17000L, 33000L, 51000L, 66000L, 100000L, 116000L, 133000L, 151000L,
            166000L, 200000L, 216000L, 233000L, 249000L, 267000L, 283000L, 300000L,
            317000L, 333000L, 351000L, 366000L, 400000L, 416000L, 433000L, 451000L,
            466000L, 483000L, 500000L, 517000L, 533000L
        )
        val result = timestamps.map { quantizer.quantize(it, 30) }
        val deltas = result.zipWithNext { a, b -> b - a }
        //passes through until the rate is confirmed, then it snaps to the half grid (16666us)
        //skipping a slot when the source drops a frame
        assertEquals(
            listOf(
                17000L, 16000L, 18000L, 15000L, 34000L, 16000L, 17000L, 18000L, 15000L,
                34000L, 16000L, 17000L, 16000L, 18000L, 16000L, 17000L, 16666L, 16667L,
                16667L, 16666L, 33334L, 16666L, 16667L, 16667L, 16666L, 16667L, 16667L,
                16666L, 16667L
            ),
            deltas
        )
    }

    @Test
    fun testAlwaysIncrease() {
        val quantizer = TimestampQuantizer()
        //two frames rendered in the same clock value must not produce the same timestamp
        val timestamps = listOf(0L, 33000L, 33000L, 66000L, 100000L, 133000L)
        val result = timestamps.map { quantizer.quantize(it, 30) }
        var last = -1L
        result.forEach {
            assertTrue("$it is not higher than $last", it > last)
            last = it
        }
    }

    @Test
    fun testNoDesyncWithClock() {
        val quantizer = TimestampQuantizer()
        //10 seconds of a 30fps source, the result must follow the clock closely
        var timestamp = 0L
        val jitter = listOf(-12000L, 5000L, 9000L, -7000L, 0L, 14000L, -3000L)
        var maxDifference = 0L
        repeat(300) {
            val clock = timestamp + jitter[it % jitter.size]
            val result = quantizer.quantize(clock, 30)
            val difference = kotlin.math.abs(result - clock)
            if (difference > maxDifference) maxDifference = difference
            timestamp += 33333L
        }
        assertTrue("max difference with the clock: $maxDifference", maxDifference <= 33333L)
    }

    @Test
    fun testResetStartAgain() {
        val quantizer = TimestampQuantizer()
        listOf(0L, 33000L, 66000L, 100000L).forEach { quantizer.quantize(it, 30) }
        quantizer.reset()
        assertEquals(0L, quantizer.quantize(0L, 30))
        assertEquals(33333L, quantizer.quantize(34000L, 30))
    }

    @Test
    fun testInvalidFpsNotModified() {
        val quantizer = TimestampQuantizer()
        val timestamps = listOf(0L, 30000L, 71000L, 95000L)
        val result = timestamps.map { quantizer.quantize(it, 0) }
        assertEquals(timestamps, result)
    }
}
