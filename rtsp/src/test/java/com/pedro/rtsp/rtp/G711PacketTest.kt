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
import com.pedro.rtsp.rtp.packets.G711Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/12/23.
 */
class G711PacketTest {

  @Test
  fun `GIVEN g711 data WHEN create rtp packet THEN get expected packet`() {
    val timestamp = 123456789L
    val fakeG711 = ByteArray(30) { 0x05 }

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeG711.size
    info.flags = 1
    val g711Packet = G711Packet(8000)
    g711Packet.setPorts(1, 2)
    g711Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    g711Packet.createAndSendPacket(ByteBuffer.wrap(fakeG711), info) {
      frames.addAll(it)
    }

    val expectedRtp = byteArrayOf(-128, -120, 0, 1, 0, 15, 18, 6, 7, 91, -51, 21).plus(fakeG711)
    val expectedTimeStamp = 987654L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + info.size
    val packetResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackAudio)
    assertEquals(1, frames.size)
    assertEquals(packetResult, frames[0])
  }
}