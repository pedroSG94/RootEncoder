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
package com.pedro.encoder.audio

import kotlin.experimental.inv

/**
 * G711A (pcm-alaw) encoder/decoder
 */
class G711Codec {

  fun configure(sampleRate: Int, channels: Int) {
    require(!(sampleRate != 8000 || channels != 1)) {
      "G711 codec only support 8000 sampleRate and mono channel"
    }
  }

  fun encode(buffer: ByteArray, offset: Int, size: Int): ByteArray {
    var j = offset
    val count = size / 2
    val out = ByteArray(count)
    for (i in 0 until count) {
      val sample = (buffer[j++].toInt() and 0xff or (buffer[j++].toInt() shl 8)).toShort()
      out[i] = linearToALawSample(sample)
    }
    return out
  }

  fun decode(src: ByteArray, offset: Int, len: Int): ByteArray {
    var j = 0
    val out = ByteArray(src.size * 2)
    for (i in 0 until len) {
      val s = aLawDecompressTable[src[i + offset].toInt() and 0xff]
      out[j++] = s.toByte()
      out[j++] = (s.toInt() shr 8).toByte()
    }
    return out
  }

  private fun linearToALawSample(mySample: Short): Byte {
    var sample = mySample
    val sign = sample.inv().toInt() shr 8 and 0x80
    if (sign != 0x80) sample = (-sample).toShort()
    if (sample > cClip) sample = cClip.toShort()
    var s: Int = if (sample >= 256) {
      val exponent = aLawCompressTable[sample.toInt() shr 8 and 0x7F].toInt()
      val mantissa = sample.toInt() shr exponent + 3 and 0x0F
      exponent shl 4 or mantissa
    } else {
      sample.toInt() shr 4
    }
    s = s xor (sign xor 0x55)
    return s.toByte()
  }

  companion object {
    private const val cClip = 32635
    /** decompress table constants  */
    private val aLawDecompressTable = shortArrayOf(
      -5504, -5248, -6016, -5760, -4480, -4224, -4992, -4736, -7552, -7296, -8064, -7808, -6528, -6272, -7040, -6784, -2752, -2624, -3008, -2880, -2240, -2112, -2496, -2368, -3776, -3648, -4032, -3904, -3264, -3136, -3520, -3392, -22016, -20992, -24064, -23040, -17920, -16896, -19968, -18944, -30208, -29184, -32256, -31232, -26112, -25088, -28160, -27136, -11008, -10496, -12032, -11520, -8960, -8448, -9984, -9472, -15104, -14592, -16128, -15616, -13056, -12544, -14080, -13568, -344, -328, -376,
      -360, -280, -264, -312, -296, -472, -456, -504, -488, -408, -392, -440, -424, -88, -72, -120, -104, -24, -8, -56, -40, -216, -200, -248, -232, -152, -136, -184, -168, -1376, -1312, -1504, -1440, -1120, -1056, -1248, -1184, -1888, -1824, -2016, -1952, -1632, -1568, -1760, -1696, -688, -656, -752, -720, -560, -528, -624, -592, -944, -912, -1008, -976, -816, -784, -880, -848, 5504, 5248, 6016, 5760, 4480, 4224, 4992, 4736, 7552, 7296, 8064, 7808, 6528, 6272, 7040, 6784, 2752, 2624,
      3008, 2880, 2240, 2112, 2496, 2368, 3776, 3648, 4032, 3904, 3264, 3136, 3520, 3392, 22016, 20992, 24064, 23040, 17920, 16896, 19968, 18944, 30208, 29184, 32256, 31232, 26112, 25088, 28160, 27136, 11008, 10496, 12032, 11520, 8960, 8448, 9984, 9472, 15104, 14592, 16128, 15616, 13056, 12544, 14080, 13568, 344, 328, 376, 360, 280, 264, 312, 296, 472, 456, 504, 488, 408, 392, 440, 424, 88, 72, 120, 104, 24, 8, 56, 40, 216, 200, 248, 232, 152, 136, 184, 168, 1376, 1312, 1504, 1440, 1120,
      1056, 1248, 1184, 1888, 1824, 2016, 1952, 1632, 1568, 1760, 1696, 688, 656, 752, 720, 560, 528, 624, 592, 944, 912, 1008, 976, 816, 784, 880, 848
    )
    private val aLawCompressTable = byteArrayOf(
      1, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    )
  }
}