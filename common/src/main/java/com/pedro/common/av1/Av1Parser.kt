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

package com.pedro.common.av1

import kotlin.experimental.or

/**
 * Created by pedro on 8/12/23.
 *
 * AV1 packets contains a sequence of OBUs.
 * Each OBU contain:
 * - header -> 1 to 2 bytes
 *
 * obu_forbidden_bit f(1)
 * obu_type f(4)
 * obu_extension_flag f(1)
 * obu_has_size_field f(1)
 * obu_reserved_1bit f(1)
 * if (obu_extension_flag == 1 )
 *   obu_extension_header()
 * }
 *
 * extension header:
 *
 * temporal_id f(3)
 * spatial_id f(2)
 * extension_header_reserved_3bit f(3)
 *
 * - data length (optional depend of header) -> 1 to 8 bytes in leb128
 * - data
 */
class Av1Parser {

  fun getObuType(header: Byte): ObuType {
    val value = ((header.toInt() and 0x7F) and 0xF8) ushr 3
    return ObuType.entries.firstOrNull { it.value == value } ?: ObuType.RESERVED
  }

  fun getObus(av1Data: ByteArray): List<Obu> {
    val obuList = mutableListOf<Obu>()
    var index = 0
    while (index < av1Data.size) {
      val header = readHeader(av1Data, index)
      index += header.size
      val leb128Value = readLeb128(av1Data, index)
      val length = av1Data.sliceArray(index until index + leb128Value.second)
      index += length.size
      val data = av1Data.sliceArray(index until index + leb128Value.first.toInt())
      index += data.size
      val obu = Obu(header, length, data)
      obuList.add(obu)
    }
    return obuList
  }

  private fun readHeader(av1Data: ByteArray, offset: Int): ByteArray {
    val header = mutableListOf<Byte>()
    val info = av1Data[offset]
    header.add(info)
    val containExtended = ((info.toInt() ushr 2) and 0x01) == 1
    if (containExtended) header.add(av1Data[offset + 1])
    return header.toByteArray()
  }

  private fun readLeb128(data: ByteArray, offset: Int): Pair<Long, Int> {
    var result: Long = 0
    var index = 0
    var b: Byte
    do {
      b = data[offset + index]
      result = result or ((b.toLong() and 0x7F) shl (index * 7))
      index++
    } while (b.toInt() and 0x80 != 0)
    return Pair(result, index)
  }

  fun writeLeb128(length: Long) : ByteArray {
    val result = mutableListOf<Byte>()
    var remainingValue = length
    do {
      var byte = (remainingValue and 0x7F).toByte()
      remainingValue = remainingValue ushr 7
      if (remainingValue != 0L) {
        byte = (byte or 0x80.toByte())
      }
      result.add(byte)
    } while (remainingValue != 0L)
    return result.toByteArray()
  }
}