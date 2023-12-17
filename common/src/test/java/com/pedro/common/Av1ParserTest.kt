/*
 * Copyright (C) 2023 pedroSG94.
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
}