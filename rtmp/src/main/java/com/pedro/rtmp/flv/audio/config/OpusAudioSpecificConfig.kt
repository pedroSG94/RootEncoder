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

package com.pedro.rtmp.flv.audio.config

/**
 * Created by pedro on 04/12/25.
 *
 * RFC 7845 section-5.1.1
 */
class OpusAudioSpecificConfig(private val sampleRate: Int, private val channels: Int) {

  val size = 19

  fun write(buffer: ByteArray, offset: Int) {
    buffer[offset] = 'O'.code.toByte()
    buffer[offset + 1] = 'p'.code.toByte()
    buffer[offset + 2] = 'u'.code.toByte()
    buffer[offset + 3] = 's'.code.toByte()
    buffer[offset + 4] = 'H'.code.toByte()
    buffer[offset + 5] = 'e'.code.toByte()
    buffer[offset + 6] = 'a'.code.toByte()
    buffer[offset + 7] = 'd'.code.toByte()
    buffer[offset + 8] = 0x01 //version 1
    buffer[offset + 9] = channels.toByte()
    val preSkip = 3840 //this is the recommended value by the RFC
    buffer[offset + 10] = (preSkip shr 8).toByte()
    buffer[offset + 11] = preSkip.toByte()
    buffer[offset + 12] = (sampleRate shr 24).toByte()
    buffer[offset + 13] = (sampleRate shr 16).toByte()
    buffer[offset + 14] = (sampleRate shr 8).toByte()
    buffer[offset + 15] = sampleRate.toByte()
    val outputGain = 0
    buffer[offset + 16] = (outputGain shr 8).toByte()
    buffer[offset + 17] = outputGain.toByte()
    val mappingFamily = 0
    buffer[offset + 18] = mappingFamily.toByte()
  }
}