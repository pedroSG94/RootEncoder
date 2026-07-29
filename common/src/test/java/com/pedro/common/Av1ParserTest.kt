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

import com.pedro.common.av1.Av1Parser
import com.pedro.common.av1.ObuType
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Created by pedro on 17/12/23.
 */
class Av1ParserTest {

  private val parser = Av1Parser()

  @Test
  fun `GIVEN a byte header abu WHEN get type THEN get expected type`() {
    val header = 0x0a.toByte()
    val type = parser.getObuType(header)
    assertEquals(ObuType.SEQUENCE_HEADER, type)
  }

  @Test
  fun `GIVEN a long number WHEN convert in leb128 THEN get expected byte array`() {
    val num = 12345L
    val expected = byteArrayOf(-71, 96)
    val result = parser.writeLeb128(num)
    assertArrayEquals(expected, result)
  }

  @Test
  fun `GIVEN a av1data byte array WHEN get all obu THEN get expected obu`() {
    val av1data = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val header = byteArrayOf(0x0a)
    val leb128 = byteArrayOf(0x0d)
    val data = byteArrayOf(0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val obuList = parser.getObus(av1data)
    assertEquals(1, obuList.size)
    assertArrayEquals(header, obuList[0].header)
    assertArrayEquals(leb128, obuList[0].leb128)
    assertArrayEquals(data, obuList[0].data)
    assertArrayEquals(av1data, obuList[0].getFullData())
  }

  @Test
  fun `GIVEN a truncated av1data WHEN get all obu THEN discard the incomplete obu`() {
    //the leb128 declares 13 bytes of data but only 4 are present
    val av1data = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24)
    val obuList = parser.getObus(av1data)
    assertEquals(0, obuList.size)
  }

  @Test
  fun `GIVEN a av1data without obu_has_size_field WHEN get all obu THEN normalize it adding the size field`() {
    val av1data = byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x24, 0x4f)
    val obuList = parser.getObus(av1data)
    assertEquals(1, obuList.size)
    //the obu takes the rest of the buffer and obu_has_size_field is set because now it carries the size
    assertArrayEquals(byteArrayOf(0x0a), obuList[0].header)
    assertArrayEquals(byteArrayOf(0x05), obuList[0].leb128)
    assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x24, 0x4f), obuList[0].data)
    assertArrayEquals(byteArrayOf(0x0a, 0x05, 0x00, 0x00, 0x00, 0x24, 0x4f), obuList[0].getFullData())
  }

  @Test
  fun `GIVEN a normalized obu WHEN parse it again THEN get the same result`() {
    val av1data = byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x24, 0x4f)
    val normalized = parser.getObus(av1data)[0].getFullData()
    val obuList = parser.getObus(normalized)
    assertEquals(1, obuList.size)
    assertArrayEquals(normalized, obuList[0].getFullData())
    assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x24, 0x4f), obuList[0].data)
  }

  @Test
  fun `GIVEN a av1data with a temporal delimiter before the sequence header WHEN get all obu THEN get both`() {
    val temporalDelimiter = byteArrayOf(0x12, 0x00)
    val sequenceHeader = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val obuList = parser.getObus(temporalDelimiter.plus(sequenceHeader))
    assertEquals(2, obuList.size)
    assertEquals(ObuType.TEMPORAL_DELIMITER, parser.getObuType(obuList[0].header[0]))
    assertEquals(ObuType.SEQUENCE_HEADER, parser.getObuType(obuList[1].header[0]))
    assertArrayEquals(sequenceHeader, obuList[1].getFullData())
  }

  @Test
  fun `GIVEN a truncated leb128 WHEN get all obu THEN discard it instead of reading it as data`() {
    //obu_has_size_field is set but the leb128 ends with the continuation bit on
    val av1data = byteArrayOf(0x0a, 0x80.toByte())
    val obuList = parser.getObus(av1data)
    assertEquals(0, obuList.size)
  }

  @Test
  fun `GIVEN a leb128 bigger than 8 bytes WHEN get all obu THEN discard the obu`() {
    //header, a leb128 of 9 bytes with the continuation bit set plus its terminator, and 2 bytes of data
    val av1data = byteArrayOf(
      0x0a, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
      0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01, 0x11, 0x22
    )
    val obuList = parser.getObus(av1data)
    assertEquals(0, obuList.size)
  }

  @Test
  fun `GIVEN a leb128 declaring a length that doesn't fit in an Int WHEN get all obu THEN discard the obu`() {
    //leb128 of 0xFFFFFFFF, it becomes negative if it is converted to Int before validate it
    val av1data = byteArrayOf(0x0a, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x0f, 0x01, 0x02)
    val obuList = parser.getObus(av1data)
    assertEquals(0, obuList.size)
  }

  @Test
  fun `GIVEN an obu with an extension header at the end of the buffer WHEN get all obu THEN discard it`() {
    //obu_extension_flag is set but the extension byte is missing
    val av1data = byteArrayOf(0x0e)
    val obuList = parser.getObus(av1data)
    assertEquals(0, obuList.size)
  }

  @Test
  fun `GIVEN obus with an empty payload WHEN get all obu THEN get all of them`() {
    val av1data = byteArrayOf(0x0a, 0x00, 0x0a, 0x00)
    val obuList = parser.getObus(av1data)
    assertEquals(2, obuList.size)
    obuList.forEach {
      assertArrayEquals(byteArrayOf(0x00), it.leb128)
      assertEquals(0, it.data.size)
    }
  }
}