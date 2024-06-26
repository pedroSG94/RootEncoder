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

package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkType
import java.io.IOException
import java.io.InputStream
import kotlin.experimental.and

/**
 * Created by pedro on 21/04/21.
 *
 * cs id (6 bits)
 * fmt (2 bits)
 * cs id - 64 (8 or 16 bits)
 *
 * 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+
 * |fmt| cs id |
 * +-+-+-+-+-+-+-+-+
 * Chunk basic header 1
 *
 *
 * Chunk stream IDs 64-319 can be encoded in the 2-byte form of the
 * header. ID is computed as (the second byte + 64).
 *
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |fmt| 0 | cs id - 64 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * Chunk basic header 2
 *
 *
 * Chunk stream IDs 64-65599 can be encoded in the 3-byte version of
 * this field. ID is computed as ((the third byte)*256 + (the second
 * byte) + 64).
 *
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |fmt| 1 | cs id - 64 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * Chunk basic header 3
 */
class BasicHeader(val chunkType: ChunkType, val chunkStreamId: Int) {

  companion object {
    fun parseBasicHeader(input: InputStream): BasicHeader {
      val byte = input.read().toByte()
      val chunkTypeValue = 0xff and byte.toInt() ushr 6
      val chunkType = ChunkType.entries.find { it.mark.toInt() == chunkTypeValue } ?: throw IOException("Unknown chunk type value: $chunkTypeValue")
      var chunkStreamIdValue = (byte and 0x3F).toInt()
      if (chunkStreamIdValue > 63) throw IOException("Unknown chunk stream id value: $chunkStreamIdValue")
      if (chunkStreamIdValue == 0) { //Basic header 2 bytes
        chunkStreamIdValue = input.read() - 64
      } else if (chunkStreamIdValue == 1) { //Basic header 3 bytes
        val a = input.read()
        val b = input.read()
        val value = b and 0xff shl 8 and a
        chunkStreamIdValue = value - 64
      }
      return BasicHeader(chunkType, chunkStreamIdValue)
    }
  }

  fun getHeaderSize(timestamp: Int): Int {
    var size = when (chunkType) {
      ChunkType.TYPE_0 -> 12
      ChunkType.TYPE_1 -> 8
      ChunkType.TYPE_2 -> 4
      ChunkType.TYPE_3 -> 0
    }
    if (timestamp >= 0xffffff) {
      size += 4
    }
    return size
  }

  override fun toString(): String {
    return "BasicHeader chunkType: $chunkType, chunkStreamId: $chunkStreamId"
  }
}