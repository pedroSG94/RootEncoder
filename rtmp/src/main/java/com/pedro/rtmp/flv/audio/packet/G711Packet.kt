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
import java.nio.ByteBuffer
import kotlin.experimental.or

/**
 * Created by pedro on 21/12/23.
 */
class G711Packet: BasePacket() {

  private val header = ByteArray(1)
  //In microphone we are using always 16bits pcm encoding. Change me if needed
  private var audioSize = AudioSize.SND_16_BIT

  fun sendAudioInfo(audioSize: AudioSize = AudioSize.SND_16_BIT) {
    this.audioSize = audioSize
  }

  override fun createFlvPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    //header is 1 byte length
    //4 bits sound format, 2 bits sound rate, 1 bit sound size, 1 bit sound type
    //sound rate should be ignored because G711 only support 8k so we are using 5_5k by default
    header[0] = AudioSoundType.MONO.value or (audioSize.value shl 1).toByte() or
        (AudioSoundRate.SR_5_5K.value shl 2).toByte() or (AudioFormat.G711_A.value shl 4).toByte()
    val buffer = ByteArray(fixedBuffer.remaining() + header.size)
    fixedBuffer.get(buffer, header.size, fixedBuffer.remaining())
    System.arraycopy(header, 0, buffer, 0, header.size)
    val ts = info.presentationTimeUs / 1000
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.AUDIO))
  }

  override fun reset(resetInfo: Boolean) {
  }
}