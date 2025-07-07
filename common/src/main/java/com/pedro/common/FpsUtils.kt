package com.pedro.common

import android.os.Build
import android.util.Range
import androidx.annotation.RequiresApi
import kotlin.math.abs

object FpsUtils {
    fun adaptFpsRange(expectedFps: Int, fpsRanges: List<IntArray>): IntArray {
        val expectedRange = intArrayOf(expectedFps, expectedFps)
        if (fpsRanges.isEmpty()) throw IllegalArgumentException("fpsRanges is empty")
        if (fpsRanges.contains(expectedRange)) return expectedRange
        val exactFps = fpsRanges.filter { it[1] == expectedFps }
        if (exactFps.isNotEmpty()) return exactFps.sortedBy { abs(expectedFps - it[0]) }[0]
        val higherFps = fpsRanges.filter { it[1] > expectedFps }
        if (higherFps.isNotEmpty()) {
            val upper = higherFps.sortedBy { abs(expectedFps - it[1]) }[0][1]
            return higherFps.filter { it[1] == upper }.sortedBy { abs(expectedFps - it[0]) }[0]
        }
        val lowerFps = fpsRanges.filter { it[1] < expectedFps }
        if (lowerFps.isNotEmpty()) {
            val upper = lowerFps.sortedBy { abs(expectedFps - it[1]) }[0][1]
            return lowerFps.filter { it[1] == upper }.sortedBy { abs(expectedFps - it[0]) }[0]
        }
        return fpsRanges[0]
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun adaptFpsRange(expectedFps: Int, fpsRanges: List<Range<Int>>): Range<Int> {
        val result = adaptFpsRange(expectedFps, fpsRanges.map { intArrayOf(it.lower, it.upper) })
        return Range(result[0], result[1])
    }
}