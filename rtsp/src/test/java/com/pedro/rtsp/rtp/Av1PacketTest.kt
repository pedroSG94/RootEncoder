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
import com.pedro.rtsp.utils.RtpTracks
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/12/23.
 *
 * The aggregation header is the byte after the rtp header. W is always 0 so every obu element is
 * preceded by its length in leb128, and Z and Y say if the element comes fragmented from the
 * previous packet or continues in the next one.
 *
 * Each obu element travels with obu_has_size_field set to 0 and without its leb128, because the
 * length of the aggregation is already the size of the element.
 */
class Av1PacketTest {

  private val timestamp = 123456789L
  private val expectedTimeStamp = 11111111L

  private suspend fun createPackets(av1data: ByteArray): List<RtpFrame> {
    val info = MediaFrame.Info(0, av1data.size, timestamp, true)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(av1data), info, MediaFrame.Type.VIDEO)
    val frames = mutableListOf<RtpFrame>()
    Av1Packet(RtpTracks().trackVideo).apply { setSSRC(123456789) }
      .createAndSendPacket(mediaFrame) { frames.addAll(it) }
    return frames
  }

  @Test
  fun `GIVEN av1data WHEN create rtp packet THEN get expected packet`() = runTest {
    val av1data = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val frames = createPackets(av1data)

    //aggregation header 0x08 -> Z=0, Y=0, W=0, N=1, then the length 0x0e of the element.
    //the element is the header 0x0a with obu_has_size_field cleared (0x08) plus the 13 bytes of data
    val expectedRtp = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 8, 14,
      8, 0, 0, 0, 36, 79, 126, 127, 0, 104, -125, 0, -125, 2)
    assertEquals(1, frames.size)
    assertEquals(RtpFrame(expectedRtp, expectedTimeStamp, expectedRtp.size, 0), frames[0])
  }

  @Test
  fun `GIVEN 2 obus that fit WHEN create rtp packets THEN send them complete without chaining`() = runTest {
    val sequenceHeader = byteArrayOf(0x0a, 0x02, 0x11, 0x22)
    val frameObu = byteArrayOf(0x32, 0x03, 0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte())
    val frames = createPackets(sequenceHeader.plus(frameObu))

    //aggregation header 0x08 -> Z=0, Y=0, N=1. Length 0x03 and the element 0x08, 0x11, 0x22
    val expectedFirst = byteArrayOf(-128, 96, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 8, 3, 8, 17, 34)
    //aggregation header 0x00 -> Z=0, Y=0, N=0. Length 0x04 and the element 0x30, 0xaa, 0xbb, 0xcc.
    //byte 1 is -32 instead of 96 because this is the last packet of the frame, so it carries the marker
    val expectedSecond = byteArrayOf(-128, -32, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, 0, 4, 48, -86, -69, -52)
    assertEquals(2, frames.size)
    assertEquals(RtpFrame(expectedFirst, expectedTimeStamp, expectedFirst.size, 0), frames[0])
    assertEquals(RtpFrame(expectedSecond, expectedTimeStamp, expectedSecond.size, 0), frames[1])
  }

  @Test
  fun `GIVEN a temporal delimiter WHEN create rtp packets THEN don't send it`() = runTest {
    val temporalDelimiter = byteArrayOf(0x12, 0x00)
    val sequenceHeader = byteArrayOf(0x0a, 0x02, 0x11, 0x22)
    val frames = createPackets(temporalDelimiter.plus(sequenceHeader))

    //only the sequence header is sent, the same packet than sending it alone
    val expectedRtp = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 8, 3, 8, 17, 34)
    assertEquals(1, frames.size)
    assertEquals(RtpFrame(expectedRtp, expectedTimeStamp, expectedRtp.size, 0), frames[0])
  }

  @Test
  fun `GIVEN an obu bigger than the mtu WHEN create rtp packets THEN fragment it chaining Z and Y`() = runTest {
    //3000 bytes of data, so 3001 of element once the leb128 is dropped. It needs 3 packets
    val obu = byteArrayOf(0x32, 0xb8.toByte(), 0x17).plus(ByteArray(3000) { 0x55 })
    val element = byteArrayOf(0x30).plus(ByteArray(3000) { 0x55 })
    val frames = createPackets(obu)

    //rtp header, aggregation header and the leb128 with the bytes of the element carried in each packet.
    //0x48 -> Z=0 Y=1 N=1, 0xC0 -> Z=1 Y=1, 0x80 -> Z=1 Y=0. Only the last packet has the marker
    val expectedFirstHeader = byteArrayOf(-128, 96, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 72, -79, 11)
    val expectedSecondHeader = byteArrayOf(-128, 96, 0, 2, 0, -87, -118, -57, 7, 91, -51, 21, -64, -79, 11)
    val expectedThirdHeader = byteArrayOf(-128, -32, 0, 3, 0, -87, -118, -57, 7, 91, -51, 21, -128, 87)
    assertEquals(3, frames.size)
    assertArrayEquals(expectedFirstHeader, frames[0].buffer.copyOfRange(0, expectedFirstHeader.size))
    assertArrayEquals(expectedSecondHeader, frames[1].buffer.copyOfRange(0, expectedSecondHeader.size))
    assertArrayEquals(expectedThirdHeader, frames[2].buffer.copyOfRange(0, expectedThirdHeader.size))
    //the leb128 of each packet declares 1457, 1457 and 87, that is the element without losing a byte
    assertArrayEquals(element.copyOfRange(0, 1457), frames[0].buffer.copyOfRange(15, frames[0].buffer.size))
    assertArrayEquals(element.copyOfRange(1457, 2914), frames[1].buffer.copyOfRange(15, frames[1].buffer.size))
    assertArrayEquals(element.copyOfRange(2914, 3001), frames[2].buffer.copyOfRange(14, frames[2].buffer.size))
  }

  @Test
  fun `GIVEN a complete obu and a fragmented one WHEN create rtp packets THEN chain only the fragments`() = runTest {
    val sequenceHeader = byteArrayOf(0x0a, 0x02, 0x11, 0x22)
    val obu = byteArrayOf(0x32, 0xb8.toByte(), 0x17).plus(ByteArray(3000) { 0x55 })
    val frames = createPackets(sequenceHeader.plus(obu))

    //0x08 -> Z=0 Y=0 N=1 for the complete obu, then Z=0 Y=1, Z=1 Y=1 and Z=1 Y=0 for the 3 fragments
    val expectedAggregationHeaders = listOf(0x08, 0x40, 0xC0, 0x80)
    assertEquals(4, frames.size)
    assertEquals(expectedAggregationHeaders, frames.map { it.buffer[RtpConstants.RTP_HEADER_LENGTH].toInt() and 0xff })
    //the sequence header travels complete in its own packet
    val expectedFirst = byteArrayOf(-128, 96, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 8, 3, 8, 17, 34)
    assertEquals(RtpFrame(expectedFirst, expectedTimeStamp, expectedFirst.size, 0), frames[0])
  }

  @Test
  fun `GIVEN an obu that fills the payload exactly WHEN create rtp packets THEN don't fragment it`() = runTest {
    //1457 bytes of element plus 2 of leb128 are 1459, the payload limit with the default mtu
    val obu = byteArrayOf(0x32, 0xb0.toByte(), 0x0b).plus(ByteArray(1456) { 0x77 })
    val element = byteArrayOf(0x30).plus(ByteArray(1456) { 0x77 })
    val frames = createPackets(obu)

    //aggregation header 0x08 -> Z=0 Y=0 N=1, and the leb128 -79, 11 declares the 1457 bytes
    val expectedHeader = byteArrayOf(-128, -32, 0, 1, 0, -87, -118, -57, 7, 91, -51, 21, 8, -79, 11)
    assertEquals(1, frames.size)
    assertEquals(RtpConstants.MTU - 28, frames[0].buffer.size)
    assertArrayEquals(expectedHeader, frames[0].buffer.copyOfRange(0, expectedHeader.size))
    assertArrayEquals(element, frames[0].buffer.copyOfRange(15, frames[0].buffer.size))
  }
}
