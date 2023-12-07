/*
 * Copyright (C) 2023 pedroSG94.
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
import com.pedro.rtsp.rtp.packets.H265Packet
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
class H265PacketTest {

  @Test
  fun `GIVEN a small ByteBuffer raw h265 WHEN create a packet THEN get a RTP h265 packet`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05, 0x00)
    val fakeH265 = header.plus(ByteArray(300) { 0x00 })

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH265.size
    info.flags = 1

    val h265Packet = H265Packet()
    h265Packet.setPorts(1, 2)
    h265Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h265Packet.createAndSendPacket(ByteBuffer.wrap(fakeH265), info) {
      frames.add(it)
    }

    val expectedRtp = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 5, 0).plus(fakeH265.copyOfRange(header.size, fakeH265.size))
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 2 + info.size - header.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 1)
    assertEquals(expectedPacketResult, frames[0])
  }

  @Test
  fun `GIVEN a big ByteBuffer raw h265 WHEN create a packet THEN get a RTP h265 packet`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05, 0x00)
    val fakeH265 = header.plus(ByteArray(2500) { 0x00 })

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH265.size
    info.flags = 1

    val h265Packet = H265Packet()
    h265Packet.setPorts(1, 2)
    h265Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    h265Packet.createAndSendPacket(ByteBuffer.wrap(fakeH265), info) {
      frames.add(it)
    }

    val packet1Size = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH - 3
    val chunk1 = fakeH265.copyOfRange(header.size, header.size + packet1Size)
    val chunk2 = fakeH265.copyOfRange(header.size + packet1Size, fakeH265.size)
    val expectedRtp = byteArrayOf(-128, 96, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 98, 1, -126).plus(chunk1)
    val expectedRtp2 = byteArrayOf(-128, -32, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, 98, 1, 66).plus(chunk2)

    val expectedTimeStamp = 11111111L
    val expectedSize = chunk1.size + RtpConstants.RTP_HEADER_LENGTH + 3
    val expectedSize2 = chunk2.size + RtpConstants.RTP_HEADER_LENGTH + 3

    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackVideo)
    val expectedPacketResult2 = RtpFrame(expectedRtp2, expectedTimeStamp, expectedSize2, 1, 2, RtpConstants.trackVideo)

    assertNotEquals(0, frames.size)
    assertTrue(frames.size == 2)
    assertEquals(expectedPacketResult, frames[0])
    assertEquals(expectedPacketResult2, frames[1])
  }
}