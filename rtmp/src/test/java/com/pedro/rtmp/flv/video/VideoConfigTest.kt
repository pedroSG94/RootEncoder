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

import com.pedro.rtmp.flv.video.config.VideoSpecificConfigAV1
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigAVC
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigHEVC
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VideoConfigTest {

  @Test
  fun `GIVEN vps, sps and pps WHEN create a video config for sequence packet THEN return a bytearray with the config`() {
    val vps = byteArrayOf(64, 1, 12, 1, -1, -1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, 44, 9)
    val sps = byteArrayOf(66, 1, 1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, -96, 15, 8, 2, -127, 104, -76, -82, -55, 46, -26, -96, -64, -64, -64, 16)
    val pps = byteArrayOf(68, 1, -64, 102, 124, 12, -58, 64)
    val expectedConfig = byteArrayOf(1, 1, 96, 0, 0, 0, 0, 0, 0, 0, 0, 0, -103, -16, 0, -4, -3, -8, -8, 0, 0, 3, 3, -96, 0, 1, 0, 24, 64, 1, 12, 1, -1, -1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, 44, 9, -95, 0, 1, 0, 35, 66, 1, 1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, -96, 15, 8, 2, -127, 104, -76, -82, -55, 46, -26, -96, -64, -64, -64, 16, -94, 0, 1, 0, 8, 68, 1, -64, 102, 124, 12, -58, 64)

    val config = VideoSpecificConfigHEVC(sps, pps, vps)
    val data = ByteArray(config.size)
    config.write(data, 0)
    assertArrayEquals(expectedConfig, data)
  }

  @Test
  fun `GIVEN sps and pps WHEN create a video config for sequence packet THEN return a bytearray with the config`() {
    val sps = byteArrayOf(103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8)
    val pps = byteArrayOf(104, -18, 13, -117)
    val expectedConfig = byteArrayOf(1, 100, 0, 30, -1, -31, 0, 17, 103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8, 1, 0, 4, 104, -18, 13, -117)

    val config = VideoSpecificConfigAVC(sps, pps)
    val data = ByteArray(config.size)
    config.write(data, 0)
    assertArrayEquals(expectedConfig, data)
  }

  @Test
  fun `GIVEN obu sequence WHEN create a video config THEN return a bytearray with the config`() {
    val obuSequence = byteArrayOf(0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)
    val expectedConfig = byteArrayOf(0x81.toByte(), 0x04, 0x0c, 0x00, 0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e, 0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02)

    val config = VideoSpecificConfigAV1(obuSequence)
    val data = ByteArray(config.size)
    config.write(data, 0)
    assertArrayEquals(expectedConfig, data)
  }
}