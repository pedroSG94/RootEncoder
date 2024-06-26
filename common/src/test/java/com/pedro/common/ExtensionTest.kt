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

  @Test
  fun `when list of numbers converted to hex, then correct concatenated hex returned`() {
    val numbersToHex = mapOf(
      255 to "ff", 128 to "80", 8 to "08", 1 to "01", 0 to "00", -255 to "01", -1 to "ff"
    )
    val testBytes = numbersToHex.keys.map { it.toByte()}.toByteArray()
    val expectedHex = numbersToHex.values.reduce { acc, s -> acc + s }
    assertEquals(expectedHex, testBytes.bytesToHex())
  }

  @Test
  fun `GIVEN String WHEN generate hash THEN return a MD5 hash String`() {
    val fakeBuffer = "hello world"
    val expectedResult = "5eb63bbbe01eeed093cb22bb8f5acdc3"
    val md5Hash = fakeBuffer.getMd5Hash()
    assertEquals(expectedResult, md5Hash)
  }
}