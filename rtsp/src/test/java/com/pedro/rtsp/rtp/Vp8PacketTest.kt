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
import com.pedro.rtsp.rtp.packets.Vp8Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.RtpTracks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 23/07/26.
 */
class Vp8PacketTest {

  private val timestamp = 123456789L
  private val rtpHeader = byteArrayOf(0, -87, -118, -57, 7, 91, -51, 21)

  @Test
  fun `GIVEN a small vp8 frame WHEN create a packet THEN get a single RTP packet with PID`() = runTest {
    val vp8data = ByteArray(300) { it.toByte() }
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, vp8data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(vp8data), info, MediaFrame.Type.VIDEO)
    val vp8Packet = Vp8Packet(rtpTracks.trackVideo)
    vp8Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    vp8Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(1, frames.size)
    val (pidHi, pidLo) = readPictureIdBytes(frames[0])
    assertTrue("M bit must be set (15 bits PID)", pidHi.toInt() and 0x80 != 0)
    //X|S, I, PID
    val expectedRtp = byteArrayOf(-128, -32, 0, 1).plus(rtpHeader)
      .plus(byteArrayOf(-112, -128, pidHi, pidLo)).plus(vp8data)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 4 + vp8data.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    assertEquals(expectedPacketResult, frames[0])
  }

  @Test
  fun `GIVEN a big vp8 frame WHEN create a packet THEN get fragmented RTP packets with same PID`() = runTest {
    val vp8data = ByteArray(2500) { it.toByte() }
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, vp8data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(vp8data), info, MediaFrame.Type.VIDEO)
    val vp8Packet = Vp8Packet(rtpTracks.trackVideo)
    vp8Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    vp8Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(2, frames.size)
    val (pidHi, pidLo) = readPictureIdBytes(frames[0])
    val packet1Size = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH - 4
    val chunk1 = vp8data.copyOfRange(0, packet1Size)
    val chunk2 = vp8data.copyOfRange(packet1Size, vp8data.size)
    //X|S, I, PID
    val expectedRtp = byteArrayOf(-128, 96, 0, 1).plus(rtpHeader)
      .plus(byteArrayOf(-112, -128, pidHi, pidLo)).plus(chunk1)
    //X (no S), I, same PID
    val expectedRtp2 = byteArrayOf(-128, -32, 0, 2).plus(rtpHeader)
      .plus(byteArrayOf(-128, -128, pidHi, pidLo)).plus(chunk2)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 4 + chunk1.size
    val expectedSize2 = RtpConstants.RTP_HEADER_LENGTH + 4 + chunk2.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    val expectedPacketResult2 = RtpFrame(expectedRtp2, expectedTimeStamp, expectedSize2, rtpTracks.trackVideo)
    assertEquals(expectedPacketResult, frames[0])
    assertEquals(expectedPacketResult2, frames[1])
  }

  @Test
  fun `GIVEN two frames WHEN create packets THEN picture id increments by one per frame`() = runTest {
    val vp8data = ByteArray(300) { it.toByte() }
    val rtpTracks = RtpTracks()
    val vp8Packet = Vp8Packet(rtpTracks.trackVideo)
    vp8Packet.setSSRC(123456789)
    val keyInfo = MediaFrame.Info(0, vp8data.size, timestamp, true)
    val deltaInfo = MediaFrame.Info(0, vp8data.size, timestamp, false)
    val frames = mutableListOf<RtpFrame>()
    vp8Packet.createAndSendPacket(MediaFrame(ByteBuffer.wrap(vp8data), keyInfo, MediaFrame.Type.VIDEO)) { frames.addAll(it) }
    vp8Packet.createAndSendPacket(MediaFrame(ByteBuffer.wrap(vp8data), deltaInfo, MediaFrame.Type.VIDEO)) { frames.addAll(it) }

    assertEquals(2, frames.size)
    val pid1 = readPictureId(frames[0])
    val pid2 = readPictureId(frames[1])
    assertEquals((pid1 + 1) and 0x7FFF, pid2)
    //keyframe and delta frame must produce the same descriptor in VP8
    assertEquals(frames[0].buffer[RtpConstants.RTP_HEADER_LENGTH], frames[1].buffer[RtpConstants.RTP_HEADER_LENGTH])
  }

  private fun readPictureIdBytes(frame: RtpFrame): Pair<Byte, Byte> {
    return Pair(frame.buffer[RtpConstants.RTP_HEADER_LENGTH + 2], frame.buffer[RtpConstants.RTP_HEADER_LENGTH + 3])
  }

  private fun readPictureId(frame: RtpFrame): Int {
    val (pidHi, pidLo) = readPictureIdBytes(frame)
    return ((pidHi.toInt() and 0x7F) shl 8) or (pidLo.toInt() and 0xFF)
  }
}
