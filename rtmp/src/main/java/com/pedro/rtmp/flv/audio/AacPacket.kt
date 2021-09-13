/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.rtmp.flv.audio

import android.media.MediaCodec
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import java.nio.ByteBuffer
import kotlin.experimental.or

/**
 * Created by pedro on 8/04/21.
 */
class AacPacket(private val audioPacketCallback: AudioPacketCallback) {

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

  fun createFlvAudioPacket(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    //header is 2 bytes length
    //4 bits sound format, 2 bits sound rate, 1 bit sound size, 1 bit sound type
    //8 bits sound data (always 10 because we aer using aac)
    header[0] = if (isStereo) AudioSoundType.STEREO.value else AudioSoundType.MONO.value
    header[0] = header[0] or (audioSize.value shl 1).toByte()
    val soundRate = when (sampleRate) {
      44100 -> AudioSoundRate.SR_44_1K
      22050 -> AudioSoundRate.SR_22K
      11025 -> AudioSoundRate.SR_11K
      5500 -> AudioSoundRate.SR_5_5K
      else -> AudioSoundRate.SR_44_1K
    }
    header[0] = header[0] or (soundRate.value shl 2).toByte()
    header[0] = header[0] or (AudioFormat.AAC.value shl 4).toByte()
    val buffer: ByteArray
    if (!configSend) {
      val config = AudioSpecificConfig(objectType.value, sampleRate, if (isStereo) 2 else 1)
      buffer = ByteArray(config.size + header.size)
      header[1] = Type.SEQUENCE.mark
      config.write(buffer, header.size)
      configSend = true
    } else {
      header[1] = Type.RAW.mark
      buffer = ByteArray(info.size - info.offset + header.size)

      byteBuffer.get(buffer, header.size, info.size - info.offset)
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    val ts = info.presentationTimeUs / 1000
    audioPacketCallback.onAudioFrameCreated(FlvPacket(buffer, ts, buffer.size, FlvType.AUDIO))
  }

  fun reset() {
    configSend = false
  }
}