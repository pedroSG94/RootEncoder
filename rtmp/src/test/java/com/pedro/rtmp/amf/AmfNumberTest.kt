package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNumber
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfNumberTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf number`() {
    val buffer = byteArrayOf(0, 64, 52, 0, 0, 0, 0, 0, 0)

    val input = ByteArrayInputStream(buffer)
    val amfNumber = AmfData.getAmfData(input)

    assertTrue(amfNumber is AmfNumber)
    assertEquals(20.0, (amfNumber as AmfNumber).value, 0.0)
  }

  @Test
  fun `GIVEN a amf number WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(0, 64, 52, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val amfNumber = AmfNumber(20.0)
    amfNumber.writeHeader(output)
    amfNumber.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}