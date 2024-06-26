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
class Ack(
  var lastAcknowledgedPacketSequenceNumber: Int = 0,
  var rtt: Int = 0,
  var rttVariance: Int = 0,
  var availableBufferSize: Int = 0,
  var packetReceivingRate: Int = 0,
  var estimatedLinkCapacity: Int = 0,
  var receivingRate: Int = 0
): ControlPacket(ControlType.ACK) {

  fun write(ts: Int, socketId: Int) {
    super.writeHeader(ts, socketId)
    writeBody()
  }

  fun read(input: InputStream) {
    super.readHeader(input)
    readBody(input)
  }

  private fun writeBody() {
    buffer.writeUInt32(lastAcknowledgedPacketSequenceNumber and 0x7FFFFFFF) //31 bits
    buffer.writeUInt32(rtt)
    buffer.writeUInt32(rttVariance)
    buffer.writeUInt32(availableBufferSize)
    buffer.writeUInt32(packetReceivingRate)
    buffer.writeUInt32(estimatedLinkCapacity)
    buffer.writeUInt32(receivingRate)
  }

  private fun readBody(input: InputStream) {
    lastAcknowledgedPacketSequenceNumber = input.readUInt32() and 0x7FFFFFFF //31 bits
    rtt = input.readUInt32()
    rttVariance = input.readUInt32()
    availableBufferSize = input.readUInt32()
    packetReceivingRate = input.readUInt32()
    estimatedLinkCapacity = input.readUInt32()
    receivingRate = input.readUInt32()
  }

  override fun toString(): String {
    return "${super.toString()}, Ack(lastAcknowledgedPacketSequenceNumber=$lastAcknowledgedPacketSequenceNumber, rtt=$rtt, rttVariance=$rttVariance, availableBufferSize=$availableBufferSize, packetReceivingRate=$packetReceivingRate, estimatedLinkCapacity=$estimatedLinkCapacity, receivingRate=$receivingRate)"
  }
}