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

package com.pedro.srt.srt

import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.control.Ack
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.CongestionWarning
import com.pedro.srt.srt.packets.control.DropReq
import com.pedro.srt.srt.packets.control.KeepAlive
import com.pedro.srt.srt.packets.control.Nak
import com.pedro.srt.srt.packets.control.PeerError
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.Handshake
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Created by pedro on 9/9/23.
 */
class SrtPacketTest {

  @Test
  fun `GIVEN a data packet buffer WHEN read buffer THEN get data packet`() {
    val dataPacket = byteArrayOf(0, 0, 0, 5, -128, 0, 0, 1, 0, 0, 9, -60, 0, 0, 0, 64, 0)
    val packet = SrtPacket.getSrtPacket(dataPacket)
    assertTrue(packet is DataPacket)
  }

  @Test
  fun `GIVEN a ack2 packet buffer WHEN read buffer THEN get ack2 packet`() {
    val ack2Packet = byteArrayOf(-128, 6, 0, 0, 0, 0, 0, 5, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 0)
    val packet = SrtPacket.getSrtPacket(ack2Packet)
    assertTrue(packet is Ack2)
  }

  @Test
  fun `GIVEN a ack packet buffer WHEN read buffer THEN get ack packet`() {
    val ackPacket = byteArrayOf(-128, 2, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5, 0, 0, 0, 5)
    val packet = SrtPacket.getSrtPacket(ackPacket)
    assertTrue(packet is Ack)
  }

  @Test
  fun `GIVEN a congestion warning packet buffer WHEN read buffer THEN get congestion warning packet`() {
    val congestionWarningPacket = byteArrayOf(-128, 4, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 0)
    val packet = SrtPacket.getSrtPacket(congestionWarningPacket)
    assertTrue(packet is CongestionWarning)
  }

  @Test
  fun `GIVEN a drop req packet buffer WHEN read buffer THEN get drop req packet`() {
    val dropReqPacket = byteArrayOf(-128, 7, 0, 0, 0, 0, 0, 5, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, 0, 0, 0, 8)
    val packet = SrtPacket.getSrtPacket(dropReqPacket)
    assertTrue(packet is DropReq)
  }

  @Test
  fun `GIVEN a handshake packet buffer WHEN read buffer THEN get handshake packet`() {
    val handshakePacket = byteArrayOf(-128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 4, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 5, -36, 0, 0, 32, 0, -1, -1, -1, -1, 45, 116, -9, 30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, 1, 4, 4, 0, 0, 0, 63, 0, 120, 0, 0, 0, 5, 0, 3, 108, 98, 117, 112, 58, 104, 115, 105, 116, 115, 101, 116)
    val packet = SrtPacket.getSrtPacket(handshakePacket)
    assertTrue(packet is Handshake)
  }

  @Test
  fun `GIVEN a keep alive packet buffer WHEN read buffer THEN get keep alive packet`() {
    val keepAlivePacket = byteArrayOf(-128, 1, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 0)
    val packet = SrtPacket.getSrtPacket(keepAlivePacket)
    assertTrue(packet is KeepAlive)
  }

  @Test
  fun `GIVEN a nak packet buffer WHEN read buffer THEN get nak packet`() {
    val nakPacket = byteArrayOf(-128, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 1, -128, 0, 0, 7, 0, 0, 0, 9)
    val packet = SrtPacket.getSrtPacket(nakPacket)
    assertTrue(packet is Nak)
  }

  @Test
  fun `GIVEN a peer error packet buffer WHEN read buffer THEN get peer error packet`() {
    val peerErrorPacket = byteArrayOf(-128, 8, 0, 0, 0, 0, 0, -1, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 0)
    val packet = SrtPacket.getSrtPacket(peerErrorPacket)
    assertTrue(packet is PeerError)
  }

  @Test
  fun `GIVEN a shutdown packet buffer WHEN read buffer THEN get shutdown packet`() {
    val shutdownPacket = byteArrayOf(-128, 5, 0, 0, 0, 0, 0, 0, 0, 0, 9, -60, 0, 0, 0, 64, 0, 0, 0, 0)
    val packet = SrtPacket.getSrtPacket(shutdownPacket)
    assertTrue(packet is Shutdown)
  }
}