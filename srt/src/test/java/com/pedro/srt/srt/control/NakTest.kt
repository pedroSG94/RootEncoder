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

package com.pedro.srt.srt.control

import com.pedro.srt.Utils
import com.pedro.srt.srt.packets.control.Nak
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Created by pedro on 1/9/23.
 */
class NakTest {

  @Test(expected = RuntimeException::class)
  fun `GIVEN a nak packet WHEN write nak with list empty THEN should crash with RuntimeException`() {
    val nak = Nak()
    nak.write(2500, 0x40)
  }

  @Test
  fun `GIVEN a nak packet WHEN write packet in a buffer THEN get expected buffer and expected lost list`() {
    val expectedRanges = listOf(1 to 1, 7 to 9)
    val expectedData = byteArrayOf(-128, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, -128, 0, 0, 7, 0, 0, 0, 9)
    val nak = Nak()
    nak.addLostPacket(1)
    nak.addLostPacketsRange(7, 9)
    nak.write(2500, 0x40)
    val packetNak = nak.getData()

    assertArrayEquals(expectedData, packetNak)
    assertEquals(expectedRanges, nak.getNakRanges())
  }

  @Test
  fun `GIVEN a buffer WHEN read buffer as nak packet THEN get expected nak packet and expected lost list`() {
    val buffer = byteArrayOf(-128, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, -128, 0, 0, 7, 0, 0, 0, 9)
    val expectedRanges = listOf(1 to 1, 7 to 9)
    val expectedPacket = Nak()
    expectedPacket.addLostPacket(1)
    expectedPacket.addLostPacketsRange(7, 9)
    val packet = Nak()
    packet.read(ByteArrayInputStream(buffer))

    Utils.assertObjectEquals(expectedPacket, packet)
    assertEquals(expectedRanges, packet.getNakRanges())
  }

  @Test
  fun `GIVEN a nak packet with a list of only pair of same numbers WHEN get packets lost list THEN get the same number passed to list only one time`() {
    val expectedRanges = listOf(1 to 1)
    val packet = Nak()
    packet.addLostPacket(1)
    assertEquals(expectedRanges, packet.getNakRanges())
  }

  @Test
  fun `GIVEN a nak with a range that wraps the sequence space WHEN get ranges and count THEN handle it modularly`() {
    val packet = Nak()
    packet.addLostPacketsRange(0x7FFFFFFE, 1) //wraps: 0x7FFFFFFE, 0x7FFFFFFF, 0, 1
    assertEquals(listOf(0x7FFFFFFE to 1), packet.getNakRanges())
    assertEquals(4, packet.getLostCount())
  }
}