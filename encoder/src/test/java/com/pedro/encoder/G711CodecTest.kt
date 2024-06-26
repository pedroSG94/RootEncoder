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
}