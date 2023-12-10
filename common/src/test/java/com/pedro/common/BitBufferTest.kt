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

package com.pedro.common

import org.junit.Test

/**
 * Created by pedro on 10/12/23.
 */
class BitBufferTest {

  @Test
  fun checkGetBits() {
    val buffer = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
    val bitBuffer = BitBuffer(buffer)
    val result = bitBuffer.getBits(30, 5)
  }
}