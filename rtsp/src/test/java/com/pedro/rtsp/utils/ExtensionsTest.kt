package com.pedro.rtsp.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/4/22.
 */
class ExtensionsTest {

  @Test
  fun `GIVEN a ByteArray WHEN encode THEN get a base64 String`() {
    val fakeBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x01, 0x01)
    val expectedString = "AAAAAQEAAAABAQ=="
    val result = fakeBytes.encodeToString()
    assertEquals(expectedString, result)
  }

  @Test
  fun `GIVEN a ByteBuffer WHEN get data THEN get bytearray without startVideoCode`() {
    val fakeByteBuffer = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x01))
    val expectedResult = byteArrayOf(0x01)
    val result = fakeByteBuffer.getData()
    assertArrayEquals(expectedResult, result)
  }

  @Test
  fun `GIVEN ByteArray WHEN set long value in a position with a limit THEN get array with long put`() {
    val fakeBuffer = byteArrayOf(0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0)
    val expectedResult = byteArrayOf(0, 0, 0, -106, 0, 0, 0, 0, 0, 0, 0, 0)
    val value = 150L
    fakeBuffer.setLong(value, 0, 4)
    assertArrayEquals(expectedResult, fakeBuffer)
  }

  @Test
  fun `GIVEN ByteBuffer WHEN video start code has 3 bytes THEN return 3`() {
    val fakeBuffer = ByteBuffer.wrap(byteArrayOf(0x0, 0x0, 0x1, 0x0))
    val index = fakeBuffer.getVideoStartCodeSize()
    assertEquals(3, index)
  }

  @Test
  fun `GIVEN ByteBuffer WHEN video start code has 4 bytes THEN return 4`() {
    val fakeBuffer = ByteBuffer.wrap(byteArrayOf(0x0, 0x0, 0x0, 0x1, 0x0))
    val index = fakeBuffer.getVideoStartCodeSize()
    assertEquals(4, index)
  }

  @Test
  fun `GIVEN ByteBuffer WHEN video start code not found THEN return 0`() {
    val fakeBuffer = ByteBuffer.wrap(byteArrayOf(0x0, 0x0, 0x0, 0x0, 0x0))
    val index = fakeBuffer.getVideoStartCodeSize()
    assertEquals(0, index)
  }
}