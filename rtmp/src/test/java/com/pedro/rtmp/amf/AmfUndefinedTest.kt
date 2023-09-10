package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNull
import com.pedro.rtmp.amf.v0.AmfUndefined
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfUndefinedTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf undefined`() {
    val buffer = byteArrayOf(0x06)

    val input = ByteArrayInputStream(buffer)
    val amfUndefined = AmfData.getAmfData(input)

    assertTrue(amfUndefined is AmfUndefined)
  }

  @Test
  fun `GIVEN a amf undefined WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(0x06)
    val output = ByteArrayOutputStream()

    val amfUndefined = AmfUndefined()
    amfUndefined.writeHeader(output)
    amfUndefined.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}