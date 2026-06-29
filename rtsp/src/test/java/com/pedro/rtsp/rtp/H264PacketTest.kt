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
import com.pedro.rtsp.rtp.packets.H264Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.RtpTracks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/4/22.
 */
class H264PacketTest {

  @Test
  fun `GIVEN a small ByteBuffer raw h264 WHEN create a packet THEN get a RTP h264 packet`() = runTest {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(300) { 0x00 })
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, fakeH264.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(fakeH264), info, MediaFrame.Type.VIDEO)
    val h264Packet = H264Packet(rtpTracks.trackVideo)
    h264Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h264Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    val expectedRtp = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 5).plus(fakeH264.copyOfRange(header.size, fakeH264.size))
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 1 + info.size - header.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 1)
    assertEquals(expectedPacketResult, frames[0])
  }

  @Test
  fun `GIVEN a big ByteBuffer raw h264 WHEN create a packet THEN get a RTP h264 packet`() = runTest {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(2500) { 0x00 })
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, fakeH264.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(fakeH264), info, MediaFrame.Type.VIDEO)
    val h264Packet = H264Packet(rtpTracks.trackVideo)
    h264Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h264Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    val packet1Size = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH - 2
    val chunk1 = fakeH264.copyOfRange(header.size, header.size + packet1Size)
    val chunk2 = fakeH264.copyOfRange(header.size + packet1Size, fakeH264.size)
    val expectedRtp = byteArrayOf(-128, 96, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 28, -123).plus(chunk1)
    val expectedRtp2 = byteArrayOf(-128, -32, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, 28, 69).plus(chunk2)

    val expectedTimeStamp = 11111111L
    val expectedSize = chunk1.size + RtpConstants.RTP_HEADER_LENGTH + 2
    val expectedSize2 = chunk2.size + RtpConstants.RTP_HEADER_LENGTH + 2

    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    val expectedPacketResult2 = RtpFrame(expectedRtp2, expectedTimeStamp, expectedSize2, rtpTracks.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 2)
    assertEquals(expectedPacketResult, frames[0])
    assertEquals(expectedPacketResult2, frames[1])
  }
}