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
import com.pedro.srt.srt.packets.control.handshake.ExtensionField
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.srt.packets.control.handshake.HandshakeType
import com.pedro.srt.srt.packets.control.handshake.extension.ExtensionContentFlag
import com.pedro.srt.srt.packets.control.handshake.extension.HandshakeExtension
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Created by pedro on 1/9/23.
 */
class HandshakeTest {

  @Test
  fun `GIVEN a handshake packet WHEN write packet in a buffer THEN get expected buffer`() {
    val expectedData = byteArrayOf(-128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 4, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 5, -36, 0, 0, 32, 0, -1, -1, -1, -1, 45, 116, -9, 30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, 1, 5, 3, 0, 0, 0, 63, 0, 120, 0, 0, 0, 5, 0, 1, 116, 115, 101, 116)
    val handshake = Handshake(
      extensionField = ExtensionField.HS_REQ.value or ExtensionField.CONFIG.value,
      handshakeType = HandshakeType.CONCLUSION,
      handshakeExtension = HandshakeExtension(
        flags = ExtensionContentFlag.TSBPDSND.value or ExtensionContentFlag.TSBPDRCV.value or
            ExtensionContentFlag.CRYPT.value or ExtensionContentFlag.TLPKTDROP.value or
            ExtensionContentFlag.PERIODICNAK.value or ExtensionContentFlag.REXMITFLG.value,
        path = "test"
      )
    )
    handshake.write(2500, 0x40)
    val packetHandshake = handshake.getData()
    assertArrayEquals(expectedData, packetHandshake)
  }

  @Test
  fun `GIVEN a buffer WHEN read buffer as handshake packet THEN get expected handshake packet`() {
    val buffer = byteArrayOf(-128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 4, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 5, -36, 0, 0, 32, 0, 0, 0, 0, 1, 45, 116, -9, 30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val expectedPacket = Handshake()
    val packet = Handshake()
    packet.read(ByteArrayInputStream(buffer))

    Utils.assertObjectEquals(expectedPacket, packet)
  }
}