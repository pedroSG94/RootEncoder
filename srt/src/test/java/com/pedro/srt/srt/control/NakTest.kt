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

package com.pedro.srt.srt.control

import com.pedro.srt.srt.packets.control.Nak
import org.junit.Test

/**
 * Created by pedro on 1/9/23.
 */
class NakTest {

  @Test(expected = RuntimeException::class)
  fun `GIVEN a nak packet WHEN write nak with list with size not pair THEN should crash with RuntimeException`() {
    val nak = Nak(
      mutableListOf(1, 5, 7)
    )
    nak.write(2500, 0x40)
  }

  @Test
  fun `GIVEN a nak packet WHEN write packet in a buffer THEN get expected buffer`() {
//    val expectedData = byteArrayOf(-128, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 8)
//    val nak = Nak(
//      mutableListOf(1, 5, 7, 8)
//    )
//    nak.write(2500, 0x40)
//    val packetNak = nak.getData()
//
//    assertArrayEquals(expectedData, packetNak)
  }

  @Test
  fun `GIVEN a buffer WHEN read buffer as nak packet THEN get expected nak packet`() {
//    val buffer = byteArrayOf(-128, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 8)
//    val expectedPacket = Nak(mutableListOf(1, 5, 7, 8))
//    val packet = Nak()
//    packet.read(ByteArrayInputStream(buffer))
//
//    Utils.assertObjectEquals(expectedPacket, packet)
  }
}