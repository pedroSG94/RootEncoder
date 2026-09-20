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

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAudioData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class AudioEncoderCheckBufferTest {

  private val encoder = object : AudioEncoder(NO_OP_AUDIO_DATA) {
    fun acceptsBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
      return checkBuffer(ByteBuffer.allocate(bufferInfo.size), bufferInfo)
    }
  }

  @Test
  fun `WHEN buffer has codec config flag THEN checkBuffer rejects it`() {
    val bufferInfo = MediaCodec.BufferInfo().apply {
      offset = 0
      size = 2
      presentationTimeUs = 0
      flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
    }
    assertFalse(encoder.acceptsBuffer(bufferInfo))
  }

  @Test
  fun `WHEN buffer is a normal encoded frame THEN checkBuffer accepts valid timestamp`() {
    val bufferInfo = MediaCodec.BufferInfo().apply {
      offset = 0
      size = 256
      presentationTimeUs = 1_000
      flags = 0
    }
    assertTrue(encoder.acceptsBuffer(bufferInfo))
  }

  private companion object {
    private val NO_OP_AUDIO_DATA = object : GetAudioData {
      override fun getAudioData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {}
      override fun onAudioFormat(mediaFormat: MediaFormat) {}
    }
  }
}
