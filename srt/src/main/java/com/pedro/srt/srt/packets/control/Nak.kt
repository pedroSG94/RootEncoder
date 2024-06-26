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

package com.pedro.srt.srt.packets.control

import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.writeUInt32
import java.io.InputStream

/**
 * Created by pedro on 22/8/23.
 */
class Nak: ControlPacket(ControlType.NAK) {

  private val cifLostList: MutableList<Int> = mutableListOf()

  fun addLostPacket(sequenceNumber: Int) {
    cifLostList.add(sequenceNumber and 0x7FFFFFFF)
    cifLostList.add(sequenceNumber and 0x7FFFFFFF)
  }

  fun addLostPacketsRange(minValue: Int, maxValue: Int) {
    cifLostList.add(minValue or (1 shl 31))
    cifLostList.add(maxValue and 0x7FFFFFFF)
  }

  fun write(ts: Int, socketId: Int) {
    super.writeHeader(ts, socketId)
    writeBody()
  }

  fun read(input: InputStream) {
    super.readHeader(input)
    readBody(input)
  }

  private fun writeBody() {
    if (cifLostList.isEmpty() || cifLostList.size % 2 != 0) throw IllegalArgumentException("empty or not pair size list not allowed")
    cifLostList.chunked(2).forEach { range ->
      val indicator = (range[0] shr 31) and 0x01
      buffer.writeUInt32(range[0])
      if (indicator == 1) {
        buffer.writeUInt32(range[1])
      }
    }
  }

  private fun readBody(input: InputStream) {
    var isRange = false

    while (input.available() >= 4) {
      val value = input.readUInt32()
      val indicator = (value shr 31) and 0x01
      if (indicator == 0) {
        cifLostList.add(value)
        if (!isRange) cifLostList.add(value)
        isRange = false
      } else { // indicator == 1
        cifLostList.add(value)
        isRange = true
      }
    }
  }

  /**
   * Convert packets ranges to list of packets lost
   */
  fun getNakPacketsLostList(): List<Int> {
    val chunks = cifLostList.chunked(2)
    val values = mutableListOf<Int>()
    chunks.forEach { ranges ->
      val validMinValue = ranges[0] and 0x7FFFFFFF
      val validMaxValue = ranges[1] and 0x7FFFFFFF
      values.addAll((validMinValue..validMaxValue).toList())
    }
    return values
  }

  override fun toString(): String {
    return "Nak(cifLostList=$cifLostList)"
  }
}