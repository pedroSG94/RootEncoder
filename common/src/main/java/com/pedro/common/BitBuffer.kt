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

/**
 * Created by pedro on 9/12/23.
 *
 */
class BitBuffer(private val buffer: ByteArray) {

  fun getBits(offset: Int, size: Int): Int {
    val startIndex = offset / 8
    val startBit = offset % 8
    var result = 0
    var bitNum = startBit

    for (i in 0 until size) {
      val nextByte = (startBit + i) % 8 < startBit
      val currentIndex = startIndex + if (nextByte) 1 else 0
      val currentBit = (startBit + i) % 8
      val bitValue = (buffer[currentIndex].toInt() ushr 7 - currentBit) and 0x01
      result = (result shl 1) or bitValue
      bitNum++
    }
    return result
  }
}