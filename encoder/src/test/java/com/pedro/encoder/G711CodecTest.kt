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

package com.pedro.encoder

import com.pedro.encoder.audio.G711Codec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 1/12/23.
 */
class G711CodecTest {

  private val codec = G711Codec()

  @Test
  fun `WHEN encode audio PCM to G711 THEN get expected g711 buffer`() {
    val bufferPCM = byteArrayOf(24, 48, 64, 88)
    val bufferG711 = byteArrayOf(-67, -93)

    val result = codec.encode(bufferPCM, 0, bufferPCM.size)
    assertEquals(bufferG711.size, result.size)
    assertArrayEquals(bufferG711, result)
  }

  @Test
  fun `WHEN decode audio G711 to PCM THEN get expected pcm buffer`() {
    val bufferPCM = byteArrayOf(0, 49, 0, 90)
    val bufferG711 = byteArrayOf(-67, -93)

    val result = codec.decode(bufferG711, 0, bufferG711.size)
    assertEquals(bufferPCM.size, result.size)
    assertArrayEquals(bufferPCM, result)
  }

  @Test
  fun `GIVEN a codec configured to 16khz stereo WHEN encode audio PCM THEN resample before encoding`() {
    val codec = G711Codec().apply { configure(16000, 2) }
    //4 stereo frames at 16khz: (100, 300), (200, 400), (1000, 3000) and (2000, 4000)
    val bufferPCM = byteArrayOf(100, 0, 44, 1, -56, 0, -112, 1, -24, 3, -72, 11, -48, 7, -96, 15)
    //g711 always encodes to 8khz mono, so the 4 frames become 2 samples: 250 and 2500
    val bufferG711 = byteArrayOf(-38, -106)

    val result = codec.encode(bufferPCM, 0, bufferPCM.size)
    assertEquals(bufferG711.size, result.size)
    assertArrayEquals(bufferG711, result)
  }

  @Test
  fun `GIVEN a codec configured to 16khz stereo WHEN decode audio G711 THEN resample the decoded pcm`() {
    val codec = G711Codec().apply { configure(16000, 2) }
    val bufferG711 = byteArrayOf(-67, -93)

    val result = codec.decode(bufferG711, 0, bufferG711.size)
    //g711 always decodes to 8khz mono, so 2 samples become 4 frames of 2 channels
    assertEquals(2 * 2 * 2 * 2, result.size)
  }
}