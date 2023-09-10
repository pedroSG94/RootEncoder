package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfLongString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfLongStringTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf long string`() {
    val buffer = byteArrayOf(12, 0, 0, 0, 6, 114, 97, 110, 100, 111, 109)

    val input = ByteArrayInputStream(buffer)
    val amfLongString = AmfData.getAmfData(input)

    assertTrue(amfLongString is AmfLongString)
    assertEquals("random", (amfLongString as AmfLongString).value)
  }

  @Test
  fun `GIVEN a amf long string WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(12, 0, 0, 0, 6, 114, 97, 110, 100, 111, 109)
    val output = ByteArrayOutputStream()

    val amfLongString = AmfLongString("random")
    amfLongString.writeHeader(output)
    amfLongString.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}