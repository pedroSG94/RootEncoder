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
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.Constants
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
class PacketRetransmissionTest {

  @Mock
  lateinit var socket: SrtSocket

  private lateinit var timeUtilsMocked: MockedStatic<TimeUtils>
  private var nowUs = START_US
  private var sequenceNumber = 0
  //latency and mtu are owned by CommandsManager and passed in on each call
  private var latency = 120

  @Before
  fun setup() {
    nowUs = START_US
    sequenceNumber = 0
    latency = 120
    timeUtilsMocked = Mockito.mockStatic(TimeUtils::class.java)
    timeUtilsMocked.`when`<Long>(TimeUtils::getCurrentTimeMicro).then { nowUs }
  }

  @After
  fun teardown() {
    timeUtilsMocked.close()
  }

  private fun nowTs() = (nowUs - START_US).toInt()

  /**
   * Same steps CommandsManager.writeData does after building the packet
   */
  private fun sendPacket(retransmission: PacketRetransmission, payloadSize: Int = 100): Int {
    if (sequenceNumber.toUInt() > 0x7FFFFFFFu) sequenceNumber = 0
    val packet = DataPacket(
      sequenceNumber = sequenceNumber,
      packetPosition = PacketPosition.SINGLE,
      messageNumber = 1,
      ts = nowTs(),
      payload = ByteArray(payloadSize)
    )
    val sequence = sequenceNumber
    sequenceNumber++
    retransmission.addPacket(packet)
    retransmission.dropTooLatePackets(packet.ts, latency)
    packet.write()
    retransmission.trackMediaBytes(packet.getSize(), nowUs)
    return sequence
  }

  private fun establishMediaRate(retransmission: PacketRetransmission, bytesPerSecond: Int) {
    // 11 equal chunks over a 1 s trackMediaBytes window. The rate is measured on the wire size,
    // so it ends up as bytesPerSecond plus the 16 B header of each of the 11 packets
    val chunkSize = bytesPerSecond / 11
    repeat(10) {
      sendPacket(retransmission, chunkSize)
    }
    nowUs += 1_000_000
    sendPacket(retransmission, chunkSize)
  }

  private suspend fun reSendPackets(retransmission: PacketRetransmission, from: Int, to: Int = from): Int {
    return retransmission.reSendPackets(listOf(from to to), socket, nowTs(), latency, Constants.MTU)
  }

  @Test
  fun `GIVEN repeated NAKs for same packet WHEN within time gate THEN resend once and again after clock advance`() = runTest {
    val retransmission = PacketRetransmission()
    // latency 2000 ms: at +200 ms the packet is still inside the latency window
    // (200_000 + rtt/2 = 250_000 < 2_000_000); default 120 ms would mark it too late
    latency = 2000
    retransmission.updateRtt(100_000, 25_000, latency)

    val seq = sendPacket(retransmission)

    reSendPackets(retransmission, seq)
    // first NAK is honored immediately; minResendInterval = min(max(200_000, 20_000), 500_000) = 200_000 us
    verify(socket, times(1)).write(any<SrtPacket>())

    reSendPackets(retransmission, seq)
    // second NAK within 200_000 us of the retransmit is suppressed
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 200_000
    reSendPackets(retransmission, seq)
    verify(socket, times(2)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN NAK range over retransmit budget WHEN tokens refill THEN resend oldest packets first`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 1
    establishMediaRate(retransmission, 16_000)
    retransmission.updateRtt(50_000, 10_000, latency)

    // media rate = 11 * (1454 + 16) = 16_170 B/s
    // rate = max(16_170 * 1%, 8_000) = 8_000 B/s
    // capacity = max(16_170 * 0.5, max(8_000 * 120/1000, MTU=1500)) = 8_085 B
    // wire = 400 + 16 = 416 B; 19 * 416 = 7_904 fits, 20th needs 416 but only 181 B left
    val startSeq = sequenceNumber
    repeat(20) {
      sendPacket(retransmission, 400)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(20, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(19)).write(any<SrtPacket>())

    nowUs += 50_000
    // refill: 181 + 8_000 * 50_000/1_000_000 = 581 B, enough for the remaining 416 B packet
    // still in time: 50_000 + rtt/2 = 75_000 < 120_000 us; never retransmitted, so no time gate
    reSendPackets(retransmission, endSeq)
    verify(socket, times(20)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN packet near latency expiry WHEN NAK received THEN skip resend`() = runTest {
    val retransmission = PacketRetransmission()
    latency = 1000
    retransmission.updateRtt(100_000, 0, latency)

    val seq = sendPacket(retransmission)

    // (960_000 + 50_000) >= 1_000_000 -> too late, no resend; newlyReported still 1
    nowUs += 960_000
    assertEquals(1, reSendPackets(retransmission, seq))
    verify(socket, never()).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN small loss on healthy link WHEN NAK received THEN resend all lost packets immediately`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.updateRtt(10_000, 2_000, latency)

    // no media rate yet: rate = 8_000 B/s, capacity = max(0, 960, MTU=1500) = 1_500 B
    // 3 * (100 + 16) = 348 B < 1_500 B
    val startSeq = sequenceNumber
    repeat(3) {
      sendPacket(retransmission, 100)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(3, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(3)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN retransmit budget disabled WHEN NAK range over budget THEN resend every packet`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 0
    establishMediaRate(retransmission, 16_000)
    retransmission.updateRtt(50_000, 10_000, latency)

    // same scenario as the budget test, where the token bucket stops before the last packets
    val startSeq = sequenceNumber
    repeat(20) {
      sendPacket(retransmission, 400)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(20, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(20)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN retransmit budget disabled WHEN repeated NAKs for same packet THEN time gate still applies`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 0
    retransmission.updateRtt(12_000, 0, latency)
    // minResendInterval = min(max(12_000, 20_000), 30_000) = 20_000 us

    val seq = sendPacket(retransmission)

    reSendPackets(retransmission, seq)
    verify(socket, times(1)).write(any<SrtPacket>())

    reSendPackets(retransmission, seq)
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 20_000
    reSendPackets(retransmission, seq)
    verify(socket, times(2)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN loss right after stream start WHEN NAK received THEN media rate is already estimated`() = runTest {
    val retransmission = PacketRetransmission()

    // 200 ms of media is enough for a first estimation: 11 * (1316 + 16) B in 200 ms -> 73_260 B/s
    repeat(10) {
      sendPacket(retransmission, 1316)
    }
    nowUs += 200_000
    sendPacket(retransmission, 1316)

    // rate = max(73_260 * 25%, 8_000) = 18_315 B/s
    // capacity = max(73_260 * 0.5, max(18_315 * 120/1000, MTU=1500)) = 36_630 B
    // 10 * 1332 = 13_320 B fit. Waiting a full second the budget would be 1_500 B, a single packet
    val startSeq = sequenceNumber
    repeat(10) {
      sendPacket(retransmission, 1316)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(10, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(10)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN NAK for a packet already dropped from the queue WHEN counting unique lost THEN count it`() = runTest {
    val retransmission = PacketRetransmission()

    val seq = sendPacket(retransmission)
    // at +200 ms the first packet is older than the latency window and dropTooLatePackets removes it
    nowUs += 200_000
    sendPacket(retransmission)

    // no longer in the queue so it can't be resent, but it is still a lost packet
    assertEquals(1, reSendPackets(retransmission, seq))
    verify(socket, never()).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN media rate increase WHEN next window is measured THEN budget follows it at once`() = runTest {
    val retransmission = PacketRetransmission()
    establishMediaRate(retransmission, 16_000)
    establishMediaRate(retransmission, 160_000)

    // media rate jumps from 16_170 to 160_171 B/s
    // capacity = max(160_171 * 0.5, max(40_042 * 120/1000, MTU=1500)) = 80_085 B
    // 40 * (1316 + 16) = 53_280 B fit. Averaged with the old rate it would be 22_485 B, only 16 packets
    val startSeq = sequenceNumber
    repeat(40) {
      sendPacket(retransmission, 1316)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(40, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(40)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN ack reporting an out of range rtt WHEN NAK received THEN clamp it and keep resending`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.updateRtt(Int.MAX_VALUE, Int.MAX_VALUE, latency)

    // clamped to 120_000 us, so rtt / 2 leaves the packet inside the latency window.
    // Without the clamp rtt / 2 alone is bigger than the window and nothing is ever resent
    val seq = sendPacket(retransmission)

    assertEquals(1, reSendPackets(retransmission, seq))
    verify(socket, times(1)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN sequence wrap at max value WHEN NAK spans wrap THEN resend wrapped packets`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 0
    sequenceNumber = 0x7FFFFFFE

    sendPacket(retransmission)
    sendPacket(retransmission)
    sendPacket(retransmission)

    reSendPackets(retransmission, 0x7FFFFFFE, 0)
    verify(socket, times(3)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN repeated NAK for same packet WHEN already reported THEN return zero newly reported`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 0

    val seq = sendPacket(retransmission)

    assertEquals(1, reSendPackets(retransmission, seq))
    assertEquals(0, reSendPackets(retransmission, seq))
  }

  @Test
  fun `GIVEN active retransmit state WHEN reset called THEN allow immediate resend again`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 1
    establishMediaRate(retransmission, 800_000)
    retransmission.updateRtt(50_000, 10_000, latency)

    // capacity = max(800_173 * 0.5, max(960, MTU=1500)) = 400_086 B; 20 * 516 = 10_320 B fits entirely
    val startSeq = sequenceNumber
    repeat(20) {
      sendPacket(retransmission, 500)
    }
    val endSeq = sequenceNumber - 1
    reSendPackets(retransmission, startSeq, endSeq)

    retransmission.reset()
    establishMediaRate(retransmission, 800_000)
    retransmission.updateRtt(50_000, 10_000, latency)

    val resetSeq = sendPacket(retransmission, 500)
    clearInvocations(socket)

    assertEquals(1, reSendPackets(retransmission, resetSeq))
    verify(socket, times(1)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN budget exhausted on large packet WHEN smaller packet follows THEN skip both resends but report both`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.retransmitOverheadPercent = 1
    establishMediaRate(retransmission, 16_000)
    retransmission.updateRtt(50_000, 10_000, latency)

    // capacity = 8_085 B. 20 packets in [startSeq..endSeq]: 18 * 416 = 7_488 B, 597 B left
    // packet 19 wire = 584 + 16 = 600 B -> budgetExhausted
    // packet 20 wire = 68 + 16 = 84 B would still fit but is skipped too, all 20 are reported
    val startSeq = sequenceNumber
    repeat(18) {
      sendPacket(retransmission, 400)
    }
    sendPacket(retransmission, 584)
    sendPacket(retransmission, 68)
    val endSeq = sequenceNumber - 1

    assertEquals(20, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(18)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN burst capacity on healthy link WHEN NAK spans short media window THEN resend all immediately`() = runTest {
    val retransmission = PacketRetransmission()
    latency = 2000
    establishMediaRate(retransmission, 50_000)
    retransmission.updateRtt(10_000, 2_000, latency)

    // media rate = 11 * (4545 + 16) = 50_171 B/s
    // rate = max(50_171 * 25%, 8_000) = 12_542 B/s
    // capacity = max(50_171 * 0.5, max(12_542 * 2000/1000, MTU=1500)) = 25_085 B
    // 200 * (100 + 16) = 23_200 B wire < 25_085 B
    val startSeq = sequenceNumber
    repeat(200) {
      sendPacket(retransmission, 100)
    }
    val endSeq = sequenceNumber - 1

    assertEquals(200, reSendPackets(retransmission, startSeq, endSeq))
    verify(socket, times(200)).write(any<SrtPacket>())
  }

  @Test
  fun `GIVEN first NAK within minResendInterval of original send WHEN second NAK follows quickly THEN honor first and suppress second`() = runTest {
    val retransmission = PacketRetransmission()
    retransmission.updateRtt(12_000, 0, latency)
    // minResendInterval = min(max(12_000, 20_000), 30_000) = 20_000 us

    val seq = sendPacket(retransmission)

    nowUs += 10_000
    reSendPackets(retransmission, seq)
    verify(socket, times(1)).write(any<SrtPacket>())

    nowUs += 5_000
    reSendPackets(retransmission, seq)
    verify(socket, times(1)).write(any<SrtPacket>())
  }

  private companion object {
    const val START_US = 1_000_000L
  }
}
