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

/**
 * PCM to G711U encoder/decoder
 */
class G711Codec {

  private val table16to8 = ByteArray(8192)

  init {
    var p = 1
    var q = 0
    while (p <= 0x80) {
      var j = (p shl 4) - 0x10
      for (i in 0..15) {
        val v = i + q xor 0x7F
        val value1 = v.toByte()
        val value2 = (v + 128).toByte()
        var m = j
        val e = j + p
        while (m < e) {
          table16to8[m] = value1
          table16to8[8191 - m] = value2
          m++
        }
        j += p
      }
      p = p shl 1
      q += 0x10
    }
  }

  fun configure(sampleRate: Int, channels: Int) {
    require(!(sampleRate != 8000 || channels != 1)) {
      "G711 codec only support 8000 sampleRate and mono channel"
    }
  }

  fun encode(buffer: ByteArray, offset: Int, size: Int): ByteArray {
    val out = ByteArray(size / 2)
    var j = offset
    for (i in out.indices) {
      val sample = (buffer[2 * i].toInt() and 0xFF or (buffer[2 * i + 1].toInt() shl 8)).toShort()
      out[j++] = table16to8[sample.toInt() shr 4 and 0x1FFF]
    }
    return out
  }
}