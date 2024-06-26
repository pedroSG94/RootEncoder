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
import com.pedro.srt.srt.packets.control.Ack
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Created by pedro on 1/9/23.
 */
class AckTest {

  @Test
  fun `GIVEN a ack packet WHEN write packet in a buffer THEN get expected buffer`() {
    val expectedData = byteArrayOf(-128, 2, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5)
    val ack = Ack(
      lastAcknowledgedPacketSequenceNumber = 5,
      rtt = 5,
      rttVariance = 5,
      availableBufferSize = 5,
      packetReceivingRate = 5,
      estimatedLinkCapacity = 5,
      receivingRate = 5
    )
    ack.write(2500, 0x40)
    val packetAck = ack.getData()

    assertArrayEquals(expectedData, packetAck)
  }

  @Test
  fun `GIVEN a buffer WHEN read buffer as ack packet THEN get expected ack packet`() {
    val buffer = byteArrayOf(-128, 2, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5)
    val expectedPacket = Ack(
      lastAcknowledgedPacketSequenceNumber = 5,
      rtt = 5,
      rttVariance = 5,
      availableBufferSize = 5,
      packetReceivingRate = 5,
      estimatedLinkCapacity = 5,
      receivingRate = 5,
    )
    expectedPacket.ts = 2500
    expectedPacket.socketId = 0x40
    val packet = Ack()
    packet.read(ByteArrayInputStream(buffer))

    Utils.assertObjectEquals(expectedPacket, packet)
  }
}