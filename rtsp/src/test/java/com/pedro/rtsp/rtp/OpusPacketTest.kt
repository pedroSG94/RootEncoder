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
import com.pedro.rtsp.rtp.packets.OpusPacket
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/2/24.
 */
class OpusPacketTest {

  @Test
  fun `GIVEN opus data WHEN create rtp packet THEN get expected packet`() {
    val timestamp = 123456789L
    val fakeOpus = ByteArray(30) { 0x05 }

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeOpus.size
    info.flags = 1
    val opusPacket = OpusPacket(8000)
    opusPacket.setPorts(1, 2)
    opusPacket.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    opusPacket.createAndSendPacket(ByteBuffer.wrap(fakeOpus), info) {
      frames.add(it)
    }

    val expectedRtp = byteArrayOf(-128, -31, 0, 1, 0, 15, 18, 6, 7, 91, -51, 21).plus(fakeOpus)
    val expectedTimeStamp = 987654L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + info.size
    val packetResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, 1, 2, RtpConstants.trackAudio)
    assertEquals(1, frames.size)
    assertEquals(packetResult, frames[0])
  }
}