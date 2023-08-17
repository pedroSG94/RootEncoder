package com.pedro.rtmp.amf.v3

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class Amf3StringTest {

  @Test
  fun `GIVEN an AMF3String WHEN write it into a buffer THEN get an expected bytearray`() {
    val expectedByteArray = byteArrayOf(0x06, 0x19, 0x62, 0x75, 0x74, 0x74, 0x6F, 0x6E, 0x5F, 0x70, 0x72, 0x65, 0x73, 0x73)
    val amf3String = Amf3String("button_press")
    val outputStream = ByteArrayOutputStream()
    amf3String.writeHeader(outputStream)
    amf3String.writeBody(outputStream)

    val bytesResult = outputStream.toByteArray()

    assertEquals(expectedByteArray.size, bytesResult.size)
    assertEquals(expectedByteArray.size, amf3String.getSize() + 1)
    assertArrayEquals(expectedByteArray, bytesResult)
  }
}