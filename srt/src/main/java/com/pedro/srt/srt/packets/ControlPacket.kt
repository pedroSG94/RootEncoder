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

package com.pedro.srt.srt.packets

import com.pedro.srt.srt.packets.control.ControlType
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.writeUInt32
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/8/23.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+- SRT Header +-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1|         Control Type        |            Subtype            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                   Type-specific Information                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           Timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  Destination SRT Socket ID                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+- CIF -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * +                   Control Information Field                   +
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
abstract class ControlPacket(
  var controlType: ControlType,
  var subtype: ControlType = ControlType.SUB_TYPE,
  var typeSpecificInformation: Int = 0,
  var ts: Int = 0,
  var socketId: Int = 0
): SrtPacket() {

  protected fun writeHeader(ts: Int, socketId: Int) {
    val headerData = PacketType.CONTROL.value and 0xff shl 31 or (controlType.value and 0xff shl 16) or subtype.value
    buffer.writeUInt32(headerData)
    buffer.writeUInt32(typeSpecificInformation)
    buffer.writeUInt32(ts)
    buffer.writeUInt32(socketId)
  }

  protected fun readHeader(input: InputStream) {
    val headerData = input.readUInt32()
    val packetType = PacketType.from((headerData ushr 31) and 0x01)
    if (packetType != PacketType.CONTROL) {
      throw IOException("error, parsing data packet as control packet")
    }
    controlType = ControlType.from((headerData ushr 16) and 0xFF)
    val subtypeValue = headerData and 0xFFFF
    if (subtypeValue == 0) subtype = ControlType.SUB_TYPE
    else throw IOException("unknown subtype: $subtypeValue")

    typeSpecificInformation = input.readUInt32()
    ts = input.readUInt32()
    socketId = input.readUInt32()
  }

  override fun toString(): String {
    return "ControlPacket(controlType=$controlType, subtype=$subtype, typeSpecificInformation=$typeSpecificInformation, ts=$ts, socketId=$socketId)"
  }

  companion object {
    fun getType(input: InputStream): ControlType {
      val headerData = input.readUInt32()
      return ControlType.from((headerData ushr 16) and 0xFF)
    }
  }
}