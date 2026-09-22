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

import com.pedro.common.TimeUtils
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.SrtSocket
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
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

  private lateinit var timeUtilsMocked: MockedStatic<TimeUtils>
  private var nowUs = 1_000_000L

  @Before
  fun setup() {
    nowUs = 1_000_000L
    timeUtilsMocked = Mockito.mockStatic(TimeUtils::class.java)
    timeUtilsMocked.`when`<Long>(TimeUtils::getCurrentTimeMicro).then { nowUs }
  }

  @After
  fun teardown() {
    timeUtilsMocked.close()
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
    // 11 equal chunks over a 1 s trackMediaBytes window. The rate is measured on the wire size,
    // so it ends up as bytesPerSecond plus the 16 B header of each of the 11 packets
    val chunkSize = bytesPerSecond / 11
    repeat(10) {
      sendPacket(manager, chunkSize)
    }
    nowUs += 1_000_000
    sendPacket(manager, chunkSize)
  }

  @Test
  fun `GIVEN repeated NAKs for same packet WHEN within time gate THEN resend once and again after clock advance`() = runTest {
    val manager = CommandsManager()
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
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    manager.updateRtt(50_000, 10_000)

    // media rate = 11 * (1454 + 16) = 16_170 B/s
    // rate = max(16_170 * 1%, 8_000) = 8_000 B/s
    // capacity = max(16_170 * 0.5, max(8_000 * 120/1000, MTU=1500)) = 8_085 B
    // wire = 400 + 16 = 416 B; 19 * 416 = 7_904 fits, 20th needs 416 but only 181 B left
    val startSeq = manager.sequenceNumber
    repeat(20) {
      sendPacket(manager, 400)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(20, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(19)).write(any<SrtPacket>())

    nowUs += 50_000
    // refill: 181 + 8_000 * 50_000/1_000_000 = 581 B, enough for the remaining 416 B packet
    // still in time: 50_000 + rtt/2 = 75_000 < 120_000 us; never retransmitted, so no time gate
    manager.reSendPackets(listOf(endSeq to endSeq), socket)
    verify(socket, times(20)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN packet near latency expiry WHEN NAK received THEN skip resend`() = runTest {
    val manager = CommandsManager()
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
    val manager = CommandsManager()
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
  fun `GIVEN retransmit budget disabled WHEN NAK range over budget THEN resend every packet`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    manager.updateRtt(50_000, 10_000)

    // same scenario as the budget test, where the token bucket stops before the last packets
    val startSeq = manager.sequenceNumber
    repeat(20) {
      sendPacket(manager, 400)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(20, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(20)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN retransmit budget disabled WHEN repeated NAKs for same packet THEN time gate still applies`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0
    manager.updateRtt(12_000, 0)
    // minResendInterval = min(max(12_000, 20_000), 30_000) = 20_000 us

    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(1)).write(any<SrtPacket>())

    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 20_000
    manager.reSendPackets(listOf(seq to seq), socket)
    verify(socket, times(2)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN loss right after stream start WHEN NAK received THEN media rate is already estimated`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 120

    // 200 ms of media is enough for a first estimation: 11 * (1316 + 16) B in 200 ms -> 73_260 B/s
    repeat(10) {
      sendPacket(manager, 1316)
    }
    nowUs += 200_000
    sendPacket(manager, 1316)

    // rate = max(73_260 * 25%, 8_000) = 18_315 B/s
    // capacity = max(73_260 * 0.5, max(18_315 * 120/1000, MTU=1500)) = 36_630 B
    // 10 * 1332 = 13_320 B fit. Waiting a full second the budget would be 1_500 B, a single packet
    val startSeq = manager.sequenceNumber
    repeat(10) {
      sendPacket(manager, 1316)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(10, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(10)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN NAK for a packet already dropped from the queue WHEN counting unique lost THEN count it`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 120

    val seq = manager.sequenceNumber
    sendPacket(manager)
    // at +200 ms the first packet is older than the latency window and dropTooLatePackets removes it
    nowUs += 200_000
    sendPacket(manager)
    clearInvocations(socket)

    // no longer in the queue so it can't be resent, but it is still a lost packet
    assertEquals(1, manager.reSendPackets(listOf(seq to seq), socket))
    verify(socket, never()).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN media rate increase WHEN next window is measured THEN budget follows it at once`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    establishMediaRate(manager, 160_000)

    // media rate jumps from 16_170 to 160_171 B/s
    // capacity = max(160_171 * 0.5, max(40_042 * 120/1000, MTU=1500)) = 80_085 B
    // 40 * (1316 + 16) = 53_280 B fit. Averaged with the old rate it would be 22_485 B, only 16 packets
    val startSeq = manager.sequenceNumber
    repeat(40) {
      sendPacket(manager, 1316)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(40, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(40)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN ack reporting an out of range rtt WHEN NAK received THEN clamp it and keep resending`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 120
    manager.updateRtt(Int.MAX_VALUE, Int.MAX_VALUE)

    // clamped to 120_000 us, so rtt / 2 leaves the packet inside the latency window.
    // Without the clamp rtt / 2 alone is bigger than the window and nothing is ever resent
    val seq = manager.sequenceNumber
    sendPacket(manager)
    clearInvocations(socket)

    assertEquals(1, manager.reSendPackets(listOf(seq to seq), socket))
    verify(socket, times(1)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN sequence wrap at max value WHEN NAK spans wrap THEN resend wrapped packets`() = runTest {
    val manager = CommandsManager()
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
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 0

    val seq = manager.sequenceNumber
    sendPacket(manager)

    assertEquals(1, manager.reSendPackets(listOf(seq to seq), socket))
    assertEquals(0, manager.reSendPackets(listOf(seq to seq), socket))
  }

  @Test
  fun `GIVEN active retransmit state WHEN reset called THEN allow immediate resend again`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 800_000)
    manager.updateRtt(50_000, 10_000)

    // capacity = max(800_173 * 0.5, max(960, MTU=1500)) = 400_086 B; 20 * 516 = 10_320 B fits entirely
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
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.retransmitOverheadPercent = 1
    manager.latency = 120
    establishMediaRate(manager, 16_000)
    manager.updateRtt(50_000, 10_000)

    // capacity = 8_085 B. 20 packets in [startSeq..endSeq]: 18 * 416 = 7_488 B, 597 B left
    // packet 19 wire = 584 + 16 = 600 B -> budgetExhausted
    // packet 20 wire = 68 + 16 = 84 B would still fit but is skipped too, all 20 are reported
    val startSeq = manager.sequenceNumber
    repeat(18) {
      sendPacket(manager, 400)
    }
    sendPacket(manager, 584)
    sendPacket(manager, 68)
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(20, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(18)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN burst capacity on healthy link WHEN NAK spans short media window THEN resend all immediately`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 2000
    establishMediaRate(manager, 50_000)
    manager.updateRtt(10_000, 2_000)

    // media rate = 11 * (4545 + 16) = 50_171 B/s
    // rate = max(50_171 * 25%, 8_000) = 12_542 B/s
    // capacity = max(50_171 * 0.5, max(12_542 * 2000/1000, MTU=1500)) = 25_085 B
    // 200 * (100 + 16) = 23_200 B wire < 25_085 B
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
    val manager = CommandsManager()
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
