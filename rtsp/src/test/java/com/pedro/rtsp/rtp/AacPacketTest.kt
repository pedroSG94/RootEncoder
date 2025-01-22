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
import com.pedro.rtsp.rtp.packets.AacPacket
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/4/22.
 */
class AacPacketTest {

  @Test
  fun `GIVEN a ByteBuffer raw aac WHEN create a packet THEN get a RTP aac packet`() = runTest {
    val timestamp = 123456789L
    val fakeAac = ByteArray(300) { 0x00 }

    val info = MediaFrame.Info(0, fakeAac.size, timestamp, false)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(fakeAac), info, MediaFrame.Type.AUDIO)
    val aacPacket = AacPacket().apply { setAudioInfo(44100) }
    aacPacket.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    aacPacket.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    val expectedRtp = byteArrayOf(-128, -31, 0, 1, 0, 83, 19, 92, 7, 91, -51, 21, 0, 16, 9, 96).plus(fakeAac)
    val expectedTimeStamp = 5444444L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + info.size + 4
    val packetResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, RtpConstants.trackAudio)
    assertEquals(1, frames.size)
    assertEquals(packetResult, frames[0])
  }
}