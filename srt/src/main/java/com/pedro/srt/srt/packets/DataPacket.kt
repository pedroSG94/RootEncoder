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

import com.pedro.srt.srt.packets.data.KeyBasedEncryption
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.readUntil
import com.pedro.srt.utils.toBoolean
import com.pedro.srt.utils.toInt
import com.pedro.srt.utils.writeUInt32
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/8/23.
 */
class DataPacket(
  var sequenceNumber: Int = 0,
  var packetPosition: PacketPosition = PacketPosition.SINGLE,
  var order: Boolean = false,
  var encryption: KeyBasedEncryption = KeyBasedEncryption.NONE,
  var retransmitted: Boolean = false,
  var messageNumber: Int = 0,
  var ts: Int = 0,
  var socketId: Int = 0,
  var payload: ByteArray = byteArrayOf()
): SrtPacket() {

  fun write() {
    resetBuffer()
    val headerData = (PacketType.DATA.value shl 31) or (sequenceNumber and 0x7FFFFFFF)
    val info = (packetPosition.value shl 30) or (order.toInt() shl 29) or
        (encryption.value shl 27) or (retransmitted.toInt() shl 26) or messageNumber
    buffer.writeUInt32(headerData)
    buffer.writeUInt32(info)
    buffer.writeUInt32(ts)
    buffer.writeUInt32(socketId)
    buffer.write(payload)
  }

  fun read(input: InputStream) {
    sequenceNumber = input.readUInt32()
    val packetType = PacketType.from((sequenceNumber ushr 31) and 0x01)
    if (packetType != PacketType.DATA) {
      throw IOException("error, parsing control packet as data packet")
    }
    val info = input.readUInt32()
    packetPosition = PacketPosition.from((info ushr 30) and 0x03)
    order = ((info ushr 29) and 0x01).toBoolean()
    encryption = KeyBasedEncryption.from((info ushr 28) and 0x03)
    retransmitted = ((info ushr 26) and 0x01).toBoolean()
    messageNumber = info and 0x03FFFFFF
    ts = input.readUInt32()
    socketId = input.readUInt32()
    val payload = ByteArray(input.available())
    input.readUntil(payload)
    this.payload = payload
  }

  fun getSize(): Int = buffer.size()

  override fun toString(): String {
    return "DataPacket(sequenceNumber=$sequenceNumber, packetPosition=$packetPosition, order=$order, encryption=$encryption, retransmitted=$retransmitted, messageNumber=$messageNumber, ts=$ts, socketId=$socketId, payload=${payload.contentToString()})"
  }
}