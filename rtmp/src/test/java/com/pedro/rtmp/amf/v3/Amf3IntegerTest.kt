package com.pedro.rtmp.amf.v3

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class Amf3IntegerTest {

  @Test
  fun `GIVEN an AMF3Integer WHEN write it into a buffer THEN get an expected bytearray`() {
    val expectedByteArray = byteArrayOf(0x04, 0x5)
    val amf3Integer = Amf3Integer(5)
    val outputStream = ByteArrayOutputStream()
    amf3Integer.writeHeader(outputStream)
    amf3Integer.writeBody(outputStream)

    val bytesResult = outputStream.toByteArray()

    assertEquals(expectedByteArray.size, bytesResult.size)
    assertEquals(expectedByteArray.size, amf3Integer.getSize() + 1)
    assertArrayEquals(expectedByteArray, bytesResult)
  }
}