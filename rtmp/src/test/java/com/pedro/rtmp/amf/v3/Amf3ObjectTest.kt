package com.pedro.rtmp.amf.v3

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class Amf3ObjectTest {

  @Test
  fun testasd() {
    val n = 5 shl 1 or 0x01
    assertEquals(11, n)
  }

  @Test
  fun `GIVEN a byte array and a defined AMF3Object WHEN write the AMF3Object in a buffer THEN get the same byte array from the buffer`() {
    val expectedByteArray = byteArrayOf(0x0A, 0x2B, 0x01, 0x05, 0x75, 0x69, 0x0B, 0x70, 0x61, 0x72, 0x61, 0x6D, 0x19, 0x62, 0x75, 0x74, 0x74, 0x6F, 0x6E, 0x5F, 0x70, 0x72, 0x65, 0x73, 0x73, 0x04, 0x05, 0x01)
    val outputStream = ByteArrayOutputStream()
    val amf3Object = Amf3Object()
    amf3Object.setProperty("ui", "button_press")
    amf3Object.setProperty("param", 5)

    amf3Object.writeHeader(outputStream)
    amf3Object.writeBody(outputStream)

    val bytesResult = outputStream.toByteArray()
    println(expectedByteArray.map{ "%02x".format(it) }.toString())
    println(bytesResult.map{ "%02x".format(it) }.toString())

    assertEquals(expectedByteArray.size, bytesResult.size)
    assertEquals(expectedByteArray.size, amf3Object.getSize() + 1)
    assertArrayEquals(expectedByteArray, bytesResult)
  }

  @Test
  fun `GIVEN a byte array and an empty AMF3Object WHEN read from the byte array THEN get an AMF3Object equals than other AMf3Object defined`() {

  }
}