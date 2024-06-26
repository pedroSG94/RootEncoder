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

package com.pedro.rtmp.utils

import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Created by pedro on 16/08/23.
 *
 */
class BitBuffer(val buffer: ByteBuffer) {
  private var bufferPosition = buffer.position() * Byte.SIZE_BITS
  private val bufferEnd = buffer.limit() * Byte.SIZE_BITS

  val hasRemaining: Boolean
    get() = bitRemaining > 0

  val bitRemaining: Int
    get() = bufferEnd - bufferPosition + 1

  val remaining: Int
    get() = ceil(bitRemaining.toDouble() / Byte.SIZE_BITS).toInt()

  fun getBool(): Boolean {
    return getInt(1) == 1
  }

  fun get(i: Int) = getLong(i).toByte()

  fun getShort(i: Int) = getLong(i).toShort()

  fun getInt(i: Int) = getLong(i).toInt()
  
  fun getLong(i: Int): Long {
    if (!hasRemaining) {
      throw IllegalStateException("No more bits to read")
    }

    val b = buffer[bufferPosition / Byte.SIZE_BITS]
    val v = if (b < 0) b + 256 else b.toInt()
    val left = Byte.SIZE_BITS - bufferPosition % Byte.SIZE_BITS
    var rc: Long
    if (i <= left) {
      rc =
        ((v shl (bufferPosition % Byte.SIZE_BITS) and 0xFF) shr ((bufferPosition % Byte.SIZE_BITS) + (left - i))).toLong()
      bufferPosition += i
    } else {
      val then = i - left
      rc = getLong(left)
      rc = rc shl then
      rc += getLong(then)
    }

    buffer.position(ceil(bufferPosition.toDouble() / Byte.SIZE_BITS).toInt())
    return rc
  }

  fun readUE(): Int {
    var leadingZeroBits = 0
    while (!getBool()) {
      leadingZeroBits++
    }
    return if (leadingZeroBits > 0) {
      (1 shl leadingZeroBits) - 1 + getInt(leadingZeroBits)
    } else {
      0
    }
  }

  companion object {

    fun extractRbsp(buffer: ByteBuffer, headerLength: Int): ByteBuffer {
      val rbsp = ByteBuffer.allocateDirect(buffer.remaining())

      val indices = buffer.indicesOf(byteArrayOf(0x00, 0x00, 0x03))

      rbsp.put(buffer, 0, headerLength)

      var previous = buffer.position()
      indices.forEach {
        rbsp.put(buffer, previous, it + 2 - previous)
        previous = it + 3 // skip emulation_prevention_three_byte
      }
      rbsp.put(buffer, previous, buffer.limit() - previous)

      rbsp.limit(rbsp.position())
      rbsp.rewind()
      return rbsp
    }
  }
}