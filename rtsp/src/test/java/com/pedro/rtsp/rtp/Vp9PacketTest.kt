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
import com.pedro.rtsp.rtp.packets.Vp9Packet
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
class Vp9PacketTest {

  private val timestamp = 123456789L
  private val rtpHeader = byteArrayOf(0, -87, -118, -57, 7, 91, -51, 21)

  @Test
  fun `GIVEN a small vp9 keyframe WHEN create a packet THEN get a single RTP packet with PID and SS`() = runTest {
    val vp9data = ByteArray(300) { it.toByte() }
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, vp9data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(vp9data), info, MediaFrame.Type.VIDEO)
    val vp9Packet = Vp9Packet(rtpTracks.trackVideo)
    vp9Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    vp9Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(1, frames.size)
    val (pidHi, pidLo) = readPictureIdBytes(frames[0])
    assertTrue("M bit must be set (15 bits PID)", pidHi.toInt() and 0x80 != 0)
    //I|B|E|V, PID, SS (N_S=0, Y=0, G=0)
    val expectedRtp = byteArrayOf(-128, -32, 0, 1).plus(rtpHeader)
      .plus(byteArrayOf(-114, pidHi, pidLo, 0)).plus(vp9data)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 4 + vp9data.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    assertEquals(expectedPacketResult, frames[0])
  }

  @Test
  fun `GIVEN a big vp9 keyframe WHEN create a packet THEN get fragmented RTP packets with same PID`() = runTest {
    val vp9data = ByteArray(2500) { it.toByte() }
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, vp9data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(vp9data), info, MediaFrame.Type.VIDEO)
    val vp9Packet = Vp9Packet(rtpTracks.trackVideo)
    vp9Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    vp9Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(2, frames.size)
    val (pidHi, pidLo) = readPictureIdBytes(frames[0])
    val packet1Size = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH - 4
    val chunk1 = vp9data.copyOfRange(0, packet1Size)
    val chunk2 = vp9data.copyOfRange(packet1Size, vp9data.size)
    //I|B|V, PID, SS (N_S=0, Y=0, G=0)
    val expectedRtp = byteArrayOf(-128, 96, 0, 1).plus(rtpHeader)
      .plus(byteArrayOf(-118, pidHi, pidLo, 0)).plus(chunk1)
    //I|E, same PID, no SS
    val expectedRtp2 = byteArrayOf(-128, -32, 0, 2).plus(rtpHeader)
      .plus(byteArrayOf(-124, pidHi, pidLo)).plus(chunk2)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 4 + chunk1.size
    val expectedSize2 = RtpConstants.RTP_HEADER_LENGTH + 3 + chunk2.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    val expectedPacketResult2 = RtpFrame(expectedRtp2, expectedTimeStamp, expectedSize2, rtpTracks.trackVideo)
    assertEquals(expectedPacketResult, frames[0])
    assertEquals(expectedPacketResult2, frames[1])
  }

  @Test
  fun `GIVEN a small vp9 delta frame WHEN create a packet THEN get a packet with P bit and no SS`() = runTest {
    val vp9data = ByteArray(300) { it.toByte() }
    val rtpTracks = RtpTracks()
    val info = MediaFrame.Info(0, vp9data.size, timestamp, false)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(vp9data), info, MediaFrame.Type.VIDEO)
    val vp9Packet = Vp9Packet(rtpTracks.trackVideo)
    vp9Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    vp9Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(1, frames.size)
    val (pidHi, pidLo) = readPictureIdBytes(frames[0])
    //I|P|B|E, PID, no SS
    val expectedRtp = byteArrayOf(-128, -32, 0, 1).plus(rtpHeader)
      .plus(byteArrayOf(-52, pidHi, pidLo)).plus(vp9data)
    val expectedTimeStamp = 11111111L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + 3 + vp9data.size
    val expectedPacketResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, rtpTracks.trackVideo)
    assertEquals(expectedPacketResult, frames[0])
  }

  @Test
  fun `GIVEN two frames WHEN create packets THEN picture id increments by one per frame`() = runTest {
    val vp9data = ByteArray(300) { it.toByte() }
    val rtpTracks = RtpTracks()
    val vp9Packet = Vp9Packet(rtpTracks.trackVideo)
    vp9Packet.setSSRC(123456789)
    val keyInfo = MediaFrame.Info(0, vp9data.size, timestamp, true)
    val deltaInfo = MediaFrame.Info(0, vp9data.size, timestamp, false)
    val frames = mutableListOf<RtpFrame>()
    vp9Packet.createAndSendPacket(MediaFrame(ByteBuffer.wrap(vp9data), keyInfo, MediaFrame.Type.VIDEO)) { frames.addAll(it) }
    vp9Packet.createAndSendPacket(MediaFrame(ByteBuffer.wrap(vp9data), deltaInfo, MediaFrame.Type.VIDEO)) { frames.addAll(it) }

    assertEquals(2, frames.size)
    val pid1 = readPictureId(frames[0])
    val pid2 = readPictureId(frames[1])
    assertEquals((pid1 + 1) and 0x7FFF, pid2)
  }

  private fun readPictureIdBytes(frame: RtpFrame): Pair<Byte, Byte> {
    return Pair(frame.buffer[RtpConstants.RTP_HEADER_LENGTH + 1], frame.buffer[RtpConstants.RTP_HEADER_LENGTH + 2])
  }

  private fun readPictureId(frame: RtpFrame): Int {
    val (pidHi, pidLo) = readPictureIdBytes(frame)
    return ((pidHi.toInt() and 0x7F) shl 8) or (pidLo.toInt() and 0xFF)
  }
}
