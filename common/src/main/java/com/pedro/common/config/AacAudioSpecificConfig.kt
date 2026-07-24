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

package com.pedro.common.config

import com.pedro.common.AudioUtils

/**
 * Created by pedro on 29/04/21.
 *
 * ISO 14496-3
 *
 * --- core (16 bits) ---
 * audioObjectType            [5]  = 2   (AAC-LC)
 * samplingFrequencyIndex     [4]  = 8   (16000, sampleRate)
 * channelConfiguration       [4]  = 2   (stereo, channel)
 * GASpecificConfig           [3]  = 000 (frameLengthFlag, dependsOnCoreCoder, extensionFlag)
 * --- extensión SBR (21 bits) ---
 * syncExtensionType          [11] = 0x2b7
 * extensionAudioObjectType   [5]  = 5   (SBR)
 * sbrPresentFlag             [1]  = 1
 * extensionSamplingFreqIndex [4]  = 5   (32000, output sampleRate)
 * padding                    [3]  = 0
 */
class AacAudioSpecificConfig(
  private val type: AudioObjectType,
  private val sampleRate: Int,
  private val channels: Int
) {

  val size = if (type == AudioObjectType.AAC_SBR) 5 else 2

  fun calculate(): ByteArray {
    val buffer = ByteArray(size)
    val realSampleRate = if (type == AudioObjectType.AAC_SBR) sampleRate / 2 else sampleRate
    val frequency = AudioUtils.getFrequency(realSampleRate)
    buffer[0] = ((AudioObjectType.AAC_LC.value shl 3) or (frequency shr 1)).toByte()
    buffer[1] = (frequency shl 7 and 0x80).plus(channels shl 3 and 0x78).toByte()
    if (type == AudioObjectType.AAC_SBR) {
      val outputFrequency = AudioUtils.getFrequency(sampleRate)
      buffer[2] = (0x2b7 shl 3).toByte()
      buffer[3] = ((0x2b7 shl 5) or type.value).toByte()
      buffer[4] = ((0x01 shl 7) or (outputFrequency shl 3)).toByte()
    }
    return buffer
  }
}