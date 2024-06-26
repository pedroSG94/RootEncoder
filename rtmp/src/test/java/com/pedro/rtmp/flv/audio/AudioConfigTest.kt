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

package com.pedro.rtmp.flv.audio

import com.pedro.rtmp.flv.audio.config.AudioSpecificConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AudioConfigTest {

  @Test
  fun `GIVEN sps and pps WHEN create a video config for sequence packet THEN return a bytearray with the config`() {
    val sampleRate = 44100
    val isStereo = true
    val objectType = AudioObjectType.AAC_LC
    val expectedConfig = byteArrayOf(18, 16, -1, -7, 80, -128, 1, 63, -4)

    val config = AudioSpecificConfig(objectType.value, sampleRate, if (isStereo) 2 else 1)
    val data = ByteArray(config.size)
    config.write(data, 0)
    assertArrayEquals(expectedConfig, data)
  }
}