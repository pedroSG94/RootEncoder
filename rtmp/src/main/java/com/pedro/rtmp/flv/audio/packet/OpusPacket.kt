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

package com.pedro.rtmp.flv.audio.packet

import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AudioFormat
import com.pedro.rtmp.flv.audio.AudioFourCCPacketType
import com.pedro.rtmp.flv.audio.config.OpusAudioSpecificConfig

/**
 * Created by pedro on 8/04/21.
 */
class OpusPacket: BasePacket() {

  private val header = ByteArray(5)
  //first time we need send audio config
  private var configSend = false

  private var sampleRate = 48000
  private var isStereo = true

  fun sendAudioInfo(sampleRate: Int, isStereo: Boolean) {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
  }

  override suspend fun createFlvPacket(
    mediaFrame: MediaFrame,
    callback: suspend (FlvPacket) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val ts = mediaFrame.info.timestamp / 1000

    //header is 5 bytes length:
    //mark first byte as extended header
    //4 bytes codec type
    val codec = AudioFormat.OPUS.value // { "O", "p", "u", "s" }
    header[1] = (codec shr 24).toByte()
    header[2] = (codec shr 16).toByte()
    header[3] = (codec shr 8).toByte()
    header[4] = codec.toByte()

    val buffer: ByteArray
    if (!configSend) {
      header[0] = ((AudioFormat.EX_HEADER.value shl 4) or (AudioFourCCPacketType.SEQUENCE_START.value and 0x0F)).toByte()
      val config = OpusAudioSpecificConfig(sampleRate, if (isStereo) 2 else 1)
      buffer = ByteArray(config.size + header.size)
      config.write(buffer, header.size)
      configSend = true
    } else {
      header[0] = ((AudioFormat.EX_HEADER.value shl 4) or (AudioFourCCPacketType.CODED_FRAMES.value and 0x0F)).toByte()
      buffer = ByteArray(fixedBuffer.remaining() + header.size)
      fixedBuffer.get(buffer, header.size, fixedBuffer.remaining())
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.AUDIO))
  }

  override fun reset(resetInfo: Boolean) {
    configSend = false
  }
}