package com.pedro.rtmp.rtmp.message

import android.util.Log
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import java.io.IOException
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
class BasicHeader(val chunkType: ChunkType, val chunkStreamId: ChunkStreamId) {

  companion object {
    fun parseBasicHeader(byte: Byte): BasicHeader {
      val chunkTypeValue = 0xff and byte.toInt() ushr 6
      val chunkType = ChunkType.values().find { it.mark.toInt() == chunkTypeValue } ?: throw IOException("Unknown chunk type value: $chunkTypeValue")
      val chunkStreamIdValue = byte and 0x3F
      val chunkStreamId = ChunkStreamId.values().find { it.mark == chunkStreamIdValue } ?: throw IOException("Unknown chunk stream id value: $chunkStreamIdValue")
      return BasicHeader(chunkType, chunkStreamId)
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