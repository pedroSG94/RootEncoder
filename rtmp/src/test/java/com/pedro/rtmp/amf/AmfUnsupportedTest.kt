package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfUnsupported
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfUnsupportedTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf unsupported`() {
    val buffer = byteArrayOf(0x0D)

    val input = ByteArrayInputStream(buffer)
    val amfUnsupported = AmfData.getAmfData(input)

    assertTrue(amfUnsupported is AmfUnsupported)
  }

  @Test
  fun `GIVEN a amf unsupported WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(0x0D)
    val output = ByteArrayOutputStream()

    val amfUnsupported = AmfUnsupported()
    amfUnsupported.writeHeader(output)
    amfUnsupported.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}