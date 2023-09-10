package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfNullTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf null`() {
    val buffer = byteArrayOf(5)

    val input = ByteArrayInputStream(buffer)
    val amfNull = AmfData.getAmfData(input)

    assertTrue(amfNull is AmfNull)
  }

  @Test
  fun `GIVEN a amf null WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(5)
    val output = ByteArrayOutputStream()

    val amfNull = AmfNull()
    amfNull.writeHeader(output)
    amfNull.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}