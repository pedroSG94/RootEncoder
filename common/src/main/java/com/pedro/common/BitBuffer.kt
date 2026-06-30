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

package com.pedro.common

import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Created by pedro on 9/12/23.
 *
 */
class BitBuffer(val buffer: ByteBuffer) {
  var bufferPosition = buffer.position() * Byte.SIZE_BITS
    private set
  private val bufferEnd = buffer.limit() * Byte.SIZE_BITS

  val bitRemaining: Int
    get() = bufferEnd - bufferPosition

  val remaining: Int
    get() = ceil(bitRemaining.toDouble() / Byte.SIZE_BITS).toInt()

  private fun getBits(size: Int): Long {
    var result = 0L
    for (i in 0 until size) {
      val bitPosition = bufferPosition + i
      val currentIndex = bitPosition / 8
      val currentBit = bitPosition % 8
      val bitValue = (buffer[currentIndex].toInt() ushr 7 - currentBit) and 0x01
      result = (result shl 1) or bitValue.toLong()
    }
    bufferPosition += size
    return result
  }

  fun getBool() = getBits(1) == 1L

  fun getInt(i: Int) = getBits(i).toInt()

  fun getLong(i: Int) = getBits(i)

  fun skip(i: Int) {
    bufferPosition += i
  }

  fun skipBool() = skip(1)

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

  fun readUVLC(): Int {
    var leadingZeros = 0
    var value = 0
    var currentIndex = bufferPosition / 8
    var currentBit = 7 - bufferPosition % 8

    while (buffer[currentIndex].toInt() and (1 shl currentBit) == 0) {
      leadingZeros++
      if (currentBit == 0) {
        currentIndex++
        currentBit = 7
      } else {
        currentBit--
      }
    }

    repeat((0 until leadingZeros + 1).count()) {
      if (currentBit == 0) {
        currentIndex++
        currentBit = 7
      } else {
        currentBit--
      }

      value = (value shl 1) or ((buffer[currentIndex].toInt() ushr currentBit) and 1)
    }
    bufferPosition += 2 * leadingZeros + 1
    return value
  }

  fun resetPosition() {
    bufferPosition = buffer.position() * Byte.SIZE_BITS
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