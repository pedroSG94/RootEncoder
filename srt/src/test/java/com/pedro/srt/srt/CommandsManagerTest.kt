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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any

/**
 * The retransmission rules are covered by PacketRetransmissionTest, this checks the wiring
 * CommandsManager does around them
 */
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

  private suspend fun sendPacket(manager: CommandsManager, payloadSize: Int = 100): Int {
    val packet = MpegTsPacket(
      buffer = ByteArray(payloadSize),
      type = MpegType.VIDEO,
      packetPosition = PacketPosition.SINGLE,
      isKey = false
    )
    return manager.writeData(packet, socket)
  }

  @Test
  fun `GIVEN packets sent with writeData WHEN NAK received THEN media rate feeds the retransmit budget`() = runTest {
    val manager = CommandsManager()
    manager.loadStartTs()
    manager.latency = 120

    // 200 ms of media is enough for a first estimation: 11 * (1316 + 16) B in 200 ms -> 73_260 B/s
    repeat(10) {
      sendPacket(manager, 1316)
    }
    nowUs += 200_000
    sendPacket(manager, 1316)

    // capacity = max(73_260 * 0.5, max(18_315 * 120/1000, MTU=1500)) = 36_630 B, so 10 * 1332 = 13_320 B fit.
    // Tracking the media bytes before serializing the packet would measure 0 on every packet and
    // leave the budget at its minimum of 1_500 B, a single packet
    val startSeq = manager.sequenceNumber
    repeat(10) {
      sendPacket(manager, 1316)
    }
    val endSeq = manager.sequenceNumber - 1
    clearInvocations(socket)

    assertEquals(10, manager.reSendPackets(listOf(startSeq to endSeq), socket))
    verify(socket, times(10)).write(any<SrtPacket>())
  }
}
