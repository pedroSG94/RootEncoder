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
      val header = readHeader(av1Data, index) ?: break
      index += header.size
      val leb128 = if (((header[0].toInt() ushr 1) and 0x01) == 1) {
        val b = readLeb128(av1Data, index) ?: break
        index += b.size
        b
      } else {
        header[0] = (header[0].toInt() or 0x02).toByte()
        writeLeb128(av1Data.size.toLong() - index)
      }
      val leb128Length = leb128.leb128ToLength()
      if (index + leb128Length > av1Data.size) break //discard obu with invalid leb128
      val data = av1Data.sliceArray(index until index + leb128Length.toInt())
      index += data.size
      obuList.add(Obu(header, leb128, data))
    }
    return obuList
  }

  private fun readHeader(av1Data: ByteArray, offset: Int): ByteArray? {
    if (offset >= av1Data.size) return null
    val info = av1Data[offset]
    val containExtended = ((info.toInt() ushr 2) and 0x01) == 1
    if (containExtended) {
      if (offset + 1 >= av1Data.size) return null
      return byteArrayOf(info, av1Data[offset + 1])
    }
    return byteArrayOf(info)
  }

  private fun readLeb128(data: ByteArray, offset: Int): ByteArray? {
    var index = 0
    var b: Byte
    do {
      if (index >= 8 || offset + index >= data.size) return null
      b = data[offset + index]
      index++
    } while (b.toInt() and 0x80 != 0)
    return data.sliceArray(offset until offset + index)
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

  fun leb128Size(value: Int): Int {
    var size = 1
    var v = value ushr 7
    while (v != 0) {
      size++
      v = v ushr 7
    }
    return size
  }

  private fun ByteArray.leb128ToLength(): Long {
    var result = 0L
    for (i in this.indices) {
      result = result or ((this[i].toLong() and 0x7F) shl (i * 7))
    }
    return result
  }
}