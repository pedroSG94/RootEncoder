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

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/11/23.
 */
class ExtensionTest {

  @Test
  fun `remove info`() {
    val buffer = ByteBuffer.wrap(ByteArray(256) { 0x00 }.mapIndexed { index, byte -> index.toByte()  }.toByteArray())
    val info = MediaCodec.BufferInfo()
    val offset = 4
    val minusLimit = 2
    info.presentationTimeUs = 0
    info.offset = offset
    info.size = buffer.remaining() - minusLimit
    info.flags = 0

    val result = buffer.removeInfo(info)
    assertEquals(buffer.capacity() - offset - minusLimit, result.remaining())
    assertEquals(offset.toByte(), result.get(0))
    assertEquals((buffer.capacity() - 1 - minusLimit).toByte(), result.get(result.remaining() - 1))
  }
}