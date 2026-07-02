/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 10/12/23.
 */
class BitBufferTest {

  private fun bitBufferOf(vararg bytes: Int): BitBuffer {
    return BitBuffer(ByteBuffer.wrap(ByteArray(bytes.size) { bytes[it].toByte() }))
  }

  @Test
  fun checkGetBits() {
    val buffer = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68.toByte(), 0x83.toByte(), 0x00, 0x83.toByte(), 0x02))
    val bitBuffer = BitBuffer(buffer)
    bitBuffer.skip(24)
    val result = bitBuffer.getInt(5)
    val result2 = bitBuffer.getInt(4)
    val result3 = bitBuffer.getLong(4)
    assertEquals(0x04, result)
    assertEquals(8, result2)
    assertEquals(9L, result3)
  }

  @Test
  fun `GIVEN a single byte WHEN getInt 8 THEN read the whole byte`() {
    val bitBuffer = bitBufferOf(0xAB)
    assertEquals(0xAB, bitBuffer.getInt(8))
    assertEquals(8, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN two bytes WHEN getInt across the byte boundary THEN combine both bytes`() {
    val bitBuffer = bitBufferOf(0xAB, 0xCD)
    assertEquals(0xABCD, bitBuffer.getInt(16))
  }

  @Test
  fun `GIVEN two bytes WHEN getInt of a size that is not byte aligned THEN read the high bits`() {
    val bitBuffer = bitBufferOf(0xAB, 0xCD)
    assertEquals(0xABC, bitBuffer.getInt(12))
    assertEquals(12, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN an unaligned offset WHEN getInt crosses a byte boundary THEN read the right bits`() {
    val bitBuffer = bitBufferOf(0xAB, 0xCD)
    bitBuffer.skip(4)
    assertEquals(0xBC, bitBuffer.getInt(8))
    assertEquals(12, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a byte WHEN getBool repeatedly THEN read every bit as a boolean`() {
    val bitBuffer = bitBufferOf(0xA0) // 1010 0000
    assertTrue(bitBuffer.getBool())
    assertFalse(bitBuffer.getBool())
    assertTrue(bitBuffer.getBool())
    assertFalse(bitBuffer.getBool())
    assertEquals(4, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN six bytes WHEN getLong 48 THEN read a value bigger than an Int`() {
    val bitBuffer = bitBufferOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
    assertEquals(0x010203040506L, bitBuffer.getLong(48))
  }

  @Test
  fun `GIVEN all bits set WHEN getLong 63 THEN return Long MAX_VALUE`() {
    val bitBuffer = bitBufferOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
    assertEquals(Long.MAX_VALUE, bitBuffer.getLong(63))
  }

  @Test
  fun `GIVEN all bits set WHEN getLong 64 THEN keep the whole bit pattern`() {
    val bitBuffer = bitBufferOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
    assertEquals(-1L, bitBuffer.getLong(64))
  }

  @Test
  fun `GIVEN getInt 0 WHEN read THEN return 0 and do not move`() {
    val bitBuffer = bitBufferOf(0xAB)
    assertEquals(0, bitBuffer.getInt(0))
    assertEquals(0, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a buffer WHEN skip THEN advance position and keep reads aligned`() {
    val bitBuffer = bitBufferOf(0x12, 0x34, 0x56)
    assertEquals(0, bitBuffer.bufferPosition)
    bitBuffer.skip(8)
    assertEquals(8, bitBuffer.bufferPosition)
    assertEquals(0x34, bitBuffer.getInt(8))
    assertEquals(16, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a buffer WHEN skipBool THEN advance a single bit`() {
    val bitBuffer = bitBufferOf(0x40) // 0100 0000
    bitBuffer.skipBool()
    assertEquals(1, bitBuffer.bufferPosition)
    assertTrue(bitBuffer.getBool())
  }

  @Test
  fun `GIVEN a buffer WHEN reading THEN bitRemaining and remaining are updated`() {
    val bitBuffer = bitBufferOf(0x00, 0x00, 0x00)
    assertEquals(24, bitBuffer.bitRemaining)
    assertEquals(3, bitBuffer.remaining)
    bitBuffer.skip(4)
    assertEquals(20, bitBuffer.bitRemaining)
    assertEquals(3, bitBuffer.remaining) // ceil(20 / 8)
    bitBuffer.getInt(4)
    assertEquals(16, bitBuffer.bitRemaining)
    assertEquals(2, bitBuffer.remaining)
    bitBuffer.skip(16)
    assertEquals(0, bitBuffer.bitRemaining)
    assertEquals(0, bitBuffer.remaining)
  }

  @Test
  fun `GIVEN a consumed buffer WHEN resetPosition THEN read again from the start`() {
    val bitBuffer = bitBufferOf(0x5A, 0x3C)
    assertEquals(0x5A, bitBuffer.getInt(8))
    assertEquals(8, bitBuffer.bufferPosition)
    bitBuffer.resetPosition()
    assertEquals(0, bitBuffer.bufferPosition)
    assertEquals(0x5A, bitBuffer.getInt(8))
  }

  @Test
  fun `GIVEN a ByteBuffer with a non zero position WHEN create BitBuffer THEN start at that position`() {
    val buffer = ByteBuffer.wrap(byteArrayOf(0x11, 0x22, 0x33))
    buffer.position(1)
    val bitBuffer = BitBuffer(buffer)
    assertEquals(8, bitBuffer.bufferPosition)
    assertEquals(16, bitBuffer.bitRemaining)
    assertEquals(0x22, bitBuffer.getInt(8))
    bitBuffer.resetPosition()
    assertEquals(8, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN an exp golomb stream WHEN readUE THEN decode each code number`() {
    val bitBuffer = bitBufferOf(0xA6) // 1 010 011 0 -> 0, 1, 2
    assertEquals(0, bitBuffer.readUE())
    assertEquals(1, bitBuffer.readUE())
    assertEquals(2, bitBuffer.readUE())
  }

  @Test
  fun `GIVEN a longer exp golomb code WHEN readUE THEN decode the value`() {
    val bitBuffer = bitBufferOf(0x20) // 00100 000 -> 3
    assertEquals(3, bitBuffer.readUE())
    assertEquals(5, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a uvlc with no leading zeros WHEN readUVLC THEN return 0 and consume one bit`() {
    val bitBuffer = bitBufferOf(0x80) // 1000 0000
    assertEquals(0L, bitBuffer.readUVLC())
    assertEquals(1, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a uvlc with leading zeros WHEN readUVLC THEN decode value and consume 2n+1 bits`() {
    val bitBuffer = bitBufferOf(0x60) // 0110 0000
    assertEquals(2L, bitBuffer.readUVLC())
    assertEquals(3, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a uvlc whose value crosses a byte boundary WHEN readUVLC THEN decode it`() {
    // 0000 1 101 | 10... -> 4 leading zeros, terminator, value bits span into the second byte
    val bitBuffer = bitBufferOf(0x0D, 0x80)
    assertEquals(26L, bitBuffer.readUVLC())
    assertEquals(9, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a uvlc whose leading zeros cross a byte boundary WHEN readUVLC THEN decode it`() {
    // 8 leading zeros (whole first byte), terminator on the second byte, value spans into the third
    val bitBuffer = bitBufferOf(0x00, 0xD5, 0x40)
    assertEquals(425L, bitBuffer.readUVLC())
    assertEquals(17, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN an unaligned position WHEN readUVLC THEN decode from that bit offset`() {
    // skip 4 bits, then 0 1 1 -> 1 leading zero, terminator, value bit 1 -> 1 + (1 shl 1) - 1 = 2
    val bitBuffer = bitBufferOf(0x07)
    bitBuffer.skip(4)
    assertEquals(2L, bitBuffer.readUVLC())
    assertEquals(7, bitBuffer.bufferPosition)
  }

  @Test
  fun `GIVEN a stream with emulation prevention bytes WHEN extractRbsp THEN remove them`() {
    val source = ByteBuffer.wrap(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x03, 0x01))
    val rbsp = BitBuffer.extractRbsp(source, 1)
    val out = ByteArray(rbsp.remaining())
    rbsp.get(out)
    assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x01), out)
  }

  @Test
  fun `GIVEN a stream without emulation bytes WHEN extractRbsp THEN keep it untouched`() {
    val source = ByteBuffer.wrap(byteArrayOf(0x01, 0x02, 0x03))
    val rbsp = BitBuffer.extractRbsp(source, 0)
    val out = ByteArray(rbsp.remaining())
    rbsp.get(out)
    assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), out)
  }
}
