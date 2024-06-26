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

package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AudioVideoTest {

  @Test
  fun `GIVEN an audio packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(7, 18, -42, -121, 0, 0, 100, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val fakePacket = FlvPacket(
      buffer = ByteArray(100) { 0x00 },
      timeStamp = 1234567,
      length = 100,
      type = FlvType.AUDIO
    )
    val audio = Audio(fakePacket)
    audio.writeHeader(output)
    audio.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }

  @Test
  fun `GIVEN a video packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(6, 18, -42, -121, 0, 0, 100, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val fakePacket = FlvPacket(
      buffer = ByteArray(100) { 0x00 },
      timeStamp = 1234567,
      length = 100,
      type = FlvType.VIDEO
    )
    val video = Video(fakePacket)
    video.writeHeader(output)
    video.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}