package com.pedro.rtmp.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthUtilTest {

    @Test
    fun `when list of numbers converted to hex, then correct concatenated hex returned`() {
        val numbersToHex = mapOf(
            255 to "ff", 128 to "80", 8 to "08", 1 to "01", 0 to "00", -255 to "01", -1 to "ff"
        )
        val testBytes = numbersToHex.keys.map { it.toByte()}.toByteArray()
        val expectedHex = numbersToHex.values.reduce { acc, s -> acc + s }
        assertEquals(expectedHex, AuthUtil.bytesToHex(testBytes))
    }
}