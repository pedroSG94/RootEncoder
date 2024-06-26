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

import android.media.MediaCodec
import com.pedro.common.removeInfo
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AudioFormat
import com.pedro.rtmp.flv.audio.AudioObjectType
import com.pedro.rtmp.flv.audio.AudioSize
import com.pedro.rtmp.flv.audio.AudioSoundRate
import com.pedro.rtmp.flv.audio.AudioSoundType
import com.pedro.rtmp.flv.audio.config.AudioSpecificConfig
import java.nio.ByteBuffer
import kotlin.experimental.or

/**
 * Created by pedro on 8/04/21.
 */
class AacPacket: BasePacket() {

  private val header = ByteArray(2)
  //first time we need send audio config
  private var configSend = false

  private var sampleRate = 44100
  private var isStereo = true
  //In microphone we are using always 16bits pcm encoding. Change me if needed
  private var audioSize = AudioSize.SND_16_BIT
  //In encoder we are using always AAC LC. Change me if needed
  private val objectType = AudioObjectType.AAC_LC

  enum class Type(val mark: Byte) {
    SEQUENCE(0x00), RAW(0x01)
  }

  fun sendAudioInfo(sampleRate: Int, isStereo: Boolean, audioSize: AudioSize = AudioSize.SND_16_BIT) {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
    this.audioSize = audioSize
  }

  override fun createFlvPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    //header is 2 bytes length
    //4 bits sound format, 2 bits sound rate, 1 bit sound size, 1 bit sound type
    //8 bits sound data (always 10 because we are using aac)
    val type = if (isStereo) AudioSoundType.STEREO.value else AudioSoundType.MONO.value
    val soundRate = when (sampleRate) {
      44100 -> AudioSoundRate.SR_44_1K
      22050 -> AudioSoundRate.SR_22K
      11025 -> AudioSoundRate.SR_11K
      5500 -> AudioSoundRate.SR_5_5K
      else -> AudioSoundRate.SR_44_1K
    }
    header[0] = type or (audioSize.value shl 1).toByte() or (soundRate.value shl 2).toByte() or (AudioFormat.AAC.value shl 4).toByte()
    val buffer: ByteArray
    if (!configSend) {
      val config = AudioSpecificConfig(objectType.value, sampleRate, if (isStereo) 2 else 1)
      buffer = ByteArray(config.size + header.size)
      header[1] = Type.SEQUENCE.mark
      config.write(buffer, header.size)
      configSend = true
    } else {
      header[1] = Type.RAW.mark
      buffer = ByteArray(fixedBuffer.remaining() + header.size)
      fixedBuffer.get(buffer, header.size, fixedBuffer.remaining())
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    val ts = info.presentationTimeUs / 1000
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.AUDIO))
  }

  override fun reset(resetInfo: Boolean) {
    configSend = false
  }
}