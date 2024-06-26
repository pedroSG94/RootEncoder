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

package com.pedro.rtmp.flv.video

import android.media.MediaCodec
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.packet.Av1Packet
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/12/23.
 */
class Av1PacketTest {

  @Test
  fun `GIVEN av1data WHEN create flv packet THEN get expected FlvPacket`() {
    val timestamp = 123456789L
    val av1data = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val expectedConfig = byteArrayOf(-112, 97, 118, 48, 49, -127, 4, 12, 0, 10, 13, 0, 0, 0, 36, 79, 126, 127, 0, 104, -125, 0, -125, 2)
    val expectedFlvPacket = byteArrayOf(-111, 97, 118, 48, 49, 10, 13, 0, 0, 0, 36, 79, 126, 127, 0, 104, -125, 0, -125, 2)

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = av1data.size
    info.flags = 1

    val frames = mutableListOf<FlvPacket>()
    val av1Packet = Av1Packet()
    av1Packet.sendVideoInfo(ByteBuffer.wrap(av1data))
    av1Packet.createFlvPacket(ByteBuffer.wrap(av1data), info) { flvPacket ->
      assertEquals(FlvType.VIDEO, flvPacket.type)
      frames.add(flvPacket)
    }

    assertEquals(2, frames.size)
    assertArrayEquals(expectedConfig, frames[0].buffer)
    assertArrayEquals(expectedFlvPacket, frames[1].buffer)
  }
}