/*
 * Copyright (C) 2023 pedroSG94.
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
import org.junit.Test

/**
 * Created by pedro on 1/12/23.
 */
class G711CodecTest {

  private val encoder = G711Codec()

  @Test
  fun `test encode audio PCM to G711`() {
    val bufferPCM = byteArrayOf(24, 48, 64, 88)
    val bufferG711 = byteArrayOf(-67, -93)

    val result = encoder.encode(bufferPCM, 0, bufferPCM.size)
    assertArrayEquals(bufferG711, result)
  }

  @Test
  fun `test decode audio G711 to PCM`() {
    val bufferPCM = byteArrayOf(0, 49, 0, 90)
    val bufferG711 = byteArrayOf(-67, -93)

    val result = encoder.decode(bufferG711, 0, bufferG711.size)
    assertArrayEquals(bufferPCM, result)
  }
}