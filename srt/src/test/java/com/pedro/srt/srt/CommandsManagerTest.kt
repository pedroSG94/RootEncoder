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

import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.SrtSocket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any

@RunWith(MockitoJUnitRunner::class)
class CommandsManagerTest {

  @Mock
  lateinit var socket: SrtSocket

  private var nowUs = 1_000_000L

  @Before
  fun setup() {
    nowUs = 1_000_000L
  }

  private fun createManager(): CommandsManager {
    return CommandsManager { nowUs }
  }

  private suspend fun sendPacket(
    manager: CommandsManager,
    payloadSize: Int = 100
  ): Int {
    val packet = MpegTsPacket(
      buffer = ByteArray(payloadSize),
      type = MpegType.VIDEO,
      packetPosition = PacketPosition.SINGLE,
      isKey = false
    )
    return manager.writeData(packet, socket)
  }

  private suspend fun establishMediaRate(manager: CommandsManager, bytesPerSecond: Int) {
    // 11 equal chunks over a 1 s trackMediaBytes window -> measured rate ~= bytesPerSecond
    val chunkSize = bytesPerSecond / 11
    repeat(10) {
      sendPacket(manager, chunkSize)
    }
    nowUs += 1_000_000
    sendPacket(manager, chunkSize)
  }

  @Test
  fun `GIVEN repeated NAKs for same packet WHEN within time gate THEN resend once and again after clock advance`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    // latency 2000 ms: at +200 ms the packet is still inside the latency window
    // (200_000 + rtt/2 = 250_000 < 2_000_000); default 120 ms would mark it too late
    manager.latency = 2000
    manager.updateRtt(100_000, 25_000)

    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    manager.reSendPackets(listOf(seq to seq), socket)
    // first NAK is honored immediately; minResendInterval = min(max(200_000, 20_000), 500_000) = 200_000 us
    verify(socket, times(1)).write(any<SrtPacket>())

    manager.reSendPackets(listOf(seq to seq), socket)
    // second NAK within 200_000 us of the retransmit is suppressed
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 200_000
    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(2)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN NAK range over retransmit budget WHEN tokens refill THEN resend oldest packets first`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    manager.updateRtt(50_000, 10_000)

    // rate = max(16_000 * 1%, 8_000) = 8_000 B/s
    // capacity = max(16_000 * 0.5, 8_000 * 120/1000, MTU=1500) = 8_000 B
    // wire = 400 + 16 = 416 B; 19 * 416 = 7_904 fits, 20th needs 416 but only 96 B left
    val startSeq = manager.sequenceNumber
    repeat(20) {
      sendPacket(manager, 400)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(20, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(19)).write(any<SrtPacket>())

    nowUs += 50_000
    // refill: 96 + 8_000 * 50_000/1_000_000 = 496 B, enough for the remaining 416 B packet
    // still in time: 50_000 + rtt/2 = 75_000 < 120_000 us; never retransmitted, so no time gate
    manager.reSendPackets(listOf(endSeq to endSeq), socket)
    verify(socket, times(20)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN packet near latency expiry WHEN NAK received THEN skip resend`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.latency = 1000
    manager.updateRtt(100_000, 0)

    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    // (960_000 + 50_000) >= 1_000_000 -> too late, no resend; newlyReported still 1
    nowUs += 960_000
    assertEquals(1, manager.reSendPackets(listOf(seq to seq), socket))
    verify(socket, never()).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN small loss on healthy link WHEN NAK received THEN resend all lost packets immediately`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.updateRtt(10_000, 2_000)

    // no media rate yet: rate = 8_000 B/s, capacity = max(0, 960, MTU=1500) = 1_500 B
    // 3 * (100 + 16) = 348 B < 1_500 B
    val startSeq = manager.sequenceNumber
    repeat(3) {
      sendPacket(manager, 100)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(3, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(3)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN retransmit overhead disabled WHEN repeated NAKs received THEN resend without limits`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0

    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    manager.reSendPackets(listOf(seq to seq), socket)
    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(2)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN sequence wrap at max value WHEN NAK spans wrap THEN resend wrapped packets`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0
    manager.sequenceNumber = 0x7FFFFFFE

    sendPacket(manager)
    sendPacket(manager)
    sendPacket(manager)
    clearInvocations(socket)

    manager.reSendPackets(listOf(0x7FFFFFFE to 0), socket)
    verify(socket, times(3)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN repeated NAK for same packet WHEN already reported THEN return zero newly reported`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0

    val seq = manager.sequenceNumber
    sendPacket(manager)

    assertEquals(1, manager.reSendPackets(listOf(seq to seq), socket))
    assertEquals(0, manager.reSendPackets(listOf(seq to seq), socket))
  }

  @Test
  fun `GIVEN active retransmit state WHEN reset called THEN allow immediate resend again`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 800_000)
    manager.updateRtt(50_000, 10_000)

    // capacity = max(400_000, 960, MTU=1500) = 400_000 B; 20 * 516 = 10_320 B fits entirely
    val startSeq = manager.sequenceNumber
    repeat(20) {
      sendPacket(manager, 500)
    }
    val endSeq = manager.sequenceNumber - 1
    manager.reSendPackets(listOf(startSeq to endSeq), socket)

    manager.reset()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 800_000)
    manager.updateRtt(50_000, 10_000)

    val resetSeq = manager.sequenceNumber
    sendPacket(manager, 500)
    clearInvocations(socket)

    assertEquals(1, manager.reSendPackets(listOf(resetSeq to resetSeq), socket))
    verify(socket, times(1)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN budget exhausted on large packet WHEN smaller packet follows THEN skip both resends but report both`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    manager.updateRtt(50_000, 10_000)

    // 20 packets in [startSeq..endSeq]: 18 * 416 = 7_488 B; +500 B (484 payload) = 7_988 B; 12 B left
    // packet 20 wire = 68 + 16 = 84 B -> budgetExhausted; all 20 reported, only 19 retransmitted
    val startSeq = manager.sequenceNumber
    repeat(18) {
      sendPacket(manager, 400)
    }
    sendPacket(manager, 484)
    sendPacket(manager, 68)
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(20, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(19)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN burst capacity on healthy link WHEN NAK spans short media window THEN resend all immediately`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.latency = 2000
    establishMediaRate(manager, 50_000)
    manager.updateRtt(10_000, 2_000)

    // rate = max(50_000 * 25%, 8_000) = 12_500 B/s
    // capacity = max(50_000 * 0.5, 12_500 * 2000/1000, MTU=1500) = 25_000 B
    // 200 * (100 + 16) = 23_200 B wire < 25_000 B
    val startSeq = manager.sequenceNumber
    repeat(200) {
      sendPacket(manager, 100)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(200, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(200)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN first NAK within minResendInterval of original send WHEN second NAK follows quickly THEN honor first and suppress second`() = runTest {
    val manager = createManager()
    manager.loadStartTs()
    manager.updateRtt(12_000, 0)
    // minResendInterval = min(max(12_000, 20_000), 30_000) = 20_000 us

    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    nowUs += 10_000
    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 5_000
    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(1)).write(any<SrtPacket>())
  }
}
