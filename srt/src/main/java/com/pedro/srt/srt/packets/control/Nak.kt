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

package com.pedro.srt.srt.packets.control

import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.srt.packets.ControlType
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.writeUInt32
import java.io.InputStream

/**
 * Created by pedro on 22/8/23.
 */
class Nak(
  var cifLostList: MutableList<Int> = mutableListOf()
): ControlPacket(ControlType.NAK) {

  fun write(ts: Int, socketId: Int) {
    //control packet header (16 bytes)
    super.writeHeader(ts, socketId)
    writeBody()
  }

  fun read(input: InputStream) {
    super.readHeader(input)
    readBody(input)
  }

  private fun writeBody() {
    if (cifLostList.size % 2 != 0 || cifLostList.isEmpty()) throw RuntimeException("invalid list size")
    buffer.writeUInt32(cifLostList[0] and 0x7FFFFFFF)
    if (cifLostList.size > 2) {
      for (i in 1 until cifLostList.size - 1 step 2) {
        buffer.writeUInt32((cifLostList[i] and 0x7FFFFFFF) or ((1 shl 31) and 0x01))
        buffer.writeUInt32(cifLostList[i + 1] and 0x7FFFFFFF)
      }
    }
    buffer.writeUInt32(cifLostList[cifLostList.size - 1] and 0x7FFFFFFF)
  }

  private fun readBody(input: InputStream) {
    var index = 0
    while (index < 2) {
      index++
      val value = input.readUInt32()
      cifLostList.add(value and 0x7FFFFFFF)
      val bit = (value ushr 31) and 0x01
      if (bit == 1) index = 0
    }
  }
}