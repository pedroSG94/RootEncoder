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

package com.pedro.rtsp.rtp

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtp.packets.Av1Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/12/23.
 */
class Av1PacketTest {

  @Test
  fun `GIVEN av1data WHEN create rtp packet THEN get expected packet`() = runTest {
    val timestamp = 123456789L
    val av1data = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)

    val info = MediaFrame.Info(0, av1data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(av1data), info, MediaFrame.Type.VIDEO)
    val frames = mutableListOf<RtpFrame>()
    val av1Packet = Av1Packet()
    av1Packet.setSSRC(123456789)
    av1Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    val expectedRtp = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 24).plus(av1data)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + info.size + 1
    val packetResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, RtpConstants.trackVideo)
    assertEquals(1, frames.size)
    assertEquals(packetResult, frames[0])
  }
}