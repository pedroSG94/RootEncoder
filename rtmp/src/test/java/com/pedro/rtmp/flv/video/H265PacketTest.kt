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
import com.pedro.rtmp.flv.video.packet.H265Packet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 9/9/23.
 */
class H265PacketTest {

  @Test
  fun `GIVEN a h265 buffer WHEN call create a h265 packet 1 time THEN return config and expected buffer`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(300) { 0x00 })
    val expectedConfig = byteArrayOf(-112, 104, 118, 99, 49, 1, 1, 96, 0, 0, 0, 0, 0, 0, 0, 0, 0, -103, -16, 0, -4, -3, -8, -8, 0, 0, 3, 3, -96, 0, 1, 0, 24, 64, 1, 12, 1, -1, -1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, 44, 9, -95, 0, 1, 0, 35, 66, 1, 1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, -96, 15, 8, 2, -127, 104, -76, -82, -55, 46, -26, -96, -64, -64, -64, 16, -94, 0, 1, 0, 8, 68, 1, -64, 102, 124, 12, -58, 64)
    val expectedFlvPacket = byteArrayOf(-111, 104, 118, 99, 49, 0, 0, 0, 0, 0, 1, 45, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH264.size
    info.flags = 1
    val h265Packet = H265Packet()
    val vps = byteArrayOf(64, 1, 12, 1, -1, -1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, 44, 9)
    val sps = byteArrayOf(66, 1, 1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, -96, 15, 8, 2, -127, 104, -76, -82, -55, 46, -26, -96, -64, -64, -64, 16)
    val pps = byteArrayOf(68, 1, -64, 102, 124, 12, -58, 64)

    h265Packet.sendVideoInfo(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps), ByteBuffer.wrap(vps))

    val frames = mutableListOf<FlvPacket>()
    h265Packet.createFlvPacket(ByteBuffer.wrap(fakeH264), info) { flvPacket ->
      assertEquals(FlvType.VIDEO, flvPacket.type)
      frames.add(flvPacket)
    }
    assertEquals(2, frames.size)
    assertArrayEquals(expectedConfig, frames[0].buffer)
    assertArrayEquals(expectedFlvPacket, frames[1].buffer)
  }
}