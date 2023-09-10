package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfBoolean
import com.pedro.rtmp.amf.v0.AmfData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfBooleanTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf boolean`() {
    val buffer = byteArrayOf(1, 1)

    val input = ByteArrayInputStream(buffer)
    val amfBoolean = AmfData.getAmfData(input)

    assertTrue(amfBoolean is AmfBoolean)
    assertEquals(true, (amfBoolean as AmfBoolean).value)
  }

  @Test
  fun `GIVEN a amf boolean WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(1, 1)
    val output = ByteArrayOutputStream()

    val amfBoolean = AmfBoolean(true)
    amfBoolean.writeHeader(output)
    amfBoolean.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}