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

import android.media.MediaCodec
import com.pedro.rtsp.rtp.packets.H264Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
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
  fun `GIVEN a small ByteBuffer raw h264 WHEN create a packet THEN get a RTP h264 packet`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(300) { 0x00 })

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH264.size
    info.flags = 1

    val fakeSps = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
    val fakePps = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x0A, 0x0B, 0x0C)
    val h264Packet = H264Packet(fakeSps, fakePps)
    h264Packet.setPorts(1, 2)
    h264Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h264Packet.createAndSendPacket(ByteBuffer.wrap(fakeH264), info) {
      frames.addAll(it)
    }

    val expectedRtp = byteArrayOf(-128, -32, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, 5).plus(fakeH264.copyOfRange(header.size, fakeH264.size))
    val expectedStapA = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 24, 0, 7, 0, 0, 0, 1, 2, 3, 4, 0, 7, 0, 0, 0, 1, 10, 11, 12)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 1 + info.size - header.size
    val expectedStapAResult = RtpFrame(expectedStapA, expectedTimeStamp, fakePps.size + fakePps.size + 5 + RtpConstants.RTP_HEADER_LENGTH, 1, 2, RtpConstants.trackVideo)
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 2)
    assertEquals(expectedStapAResult, frames[0])
    assertEquals(expectedPacketResult, frames[1])
  }

  @Test
  fun `GIVEN a big ByteBuffer raw h264 WHEN create a packet THEN get a RTP h264 packet`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(2500) { 0x00 })

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH264.size
    info.flags = 1

    val fakeSps = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
    val fakePps = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x0A, 0x0B, 0x0C)
    val h264Packet = H264Packet(fakeSps, fakePps)
    h264Packet.setPorts(1, 2)
    h264Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h264Packet.createAndSendPacket(ByteBuffer.wrap(fakeH264), info) {
      frames.addAll(it)
    }

    val packet1Size = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH - 2
    val chunk1 = fakeH264.copyOfRange(header.size, header.size + packet1Size)
    val chunk2 = fakeH264.copyOfRange(header.size + packet1Size, fakeH264.size)
    val expectedRtp = byteArrayOf(-128, 96, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, 28, -123).plus(chunk1)
    val expectedRtp2 = byteArrayOf(-128, -32, 0, 3, 0, -87, -118, -57, 7, 91, -51, 21, 28, 69).plus(chunk2)

    val expectedStapA = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 24, 0, 7, 0, 0, 0, 1, 2, 3, 4, 0, 7, 0, 0, 0, 1, 10, 11, 12)
    val expectedTimeStamp = 11111111L
    val expectedSize = chunk1.size + RtpConstants.RTP_HEADER_LENGTH + 2
    val expectedSize2 = chunk2.size + RtpConstants.RTP_HEADER_LENGTH + 2
    val expectedStapAResult = RtpFrame(expectedStapA, expectedTimeStamp, fakePps.size + fakePps.size + 5 + RtpConstants.RTP_HEADER_LENGTH, 1, 2, RtpConstants.trackVideo)

    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackVideo)
    val expectedPacketResult2 = RtpFrame(expectedRtp2, expectedTimeStamp, expectedSize2, 1, 2, RtpConstants.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 3)
    assertEquals(expectedStapAResult, frames[0])
    assertEquals(expectedPacketResult, frames[1])
    assertEquals(expectedPacketResult2, frames[2])
  }
}