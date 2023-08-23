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

package com.pedro.srt.srt

import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.chunked
import java.io.IOException
import kotlin.jvm.Throws

/**
 * Created by pedro on 23/8/23.
 */
class CommandManager {

  //used for packet lost
  private val packetHandlingQueue = mutableListOf<DataPacket>()

  var sequenceNumber: Int = 0
  var messageNumber = 0
  var MTU = Constants.MTU
  var socketId = 0
  var startTS = 0L //microSeconds

  fun loadStartTs() {
    startTS = System.nanoTime() / 1000
  }

  fun getTs(): Int {
    return (System.nanoTime() / 1000 - startTS).toInt()
  }

  @Throws(IOException::class)
  fun writeHandshake(socket: SrtSocket?, handshake: Handshake = Handshake()) {
    handshake.write(getTs(), 0)
    socket?.write(handshake)
  }

  @Throws(IOException::class)
  fun readHandshake(socket: SrtSocket?): Handshake {
    val handshakeBuffer = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
    val handshake = SrtPacket.getSrtPacket(handshakeBuffer)
    if (handshake is Handshake) {
      return handshake
    } else {
      throw IOException("unexpected response type: ${handshake.javaClass.name}")
    }
  }

  @Throws(IOException::class)
  fun writeData(packet: MpegTsPacket, socket: SrtSocket?) {
    val chunks = packet.buffer.chunked(MTU - SrtPacket.headerSize)
    chunks.forEachIndexed { index, payload ->
      val packetPosition = if (chunks.size == 1) PacketPosition.SINGLE
      else if (index == 0) PacketPosition.FIRST
      else if (index == chunks.size - 1) PacketPosition.LAST
      else PacketPosition.MIDDLE

      val dataPacket = DataPacket(
        packetPosition = packetPosition,
        messageNumber = messageNumber,
        payload = payload,
        ts = getTs(),
        socketId = socketId
      )
      writeData(dataPacket, socket)
      messageNumber++
    }
  }

  @Throws(IOException::class)
  fun writeData(dataPacket: DataPacket, socket: SrtSocket?) {
    dataPacket.sequenceNumber = sequenceNumber
    socket?.write(dataPacket)
    sequenceNumber++
  }

  @Throws(IOException::class)
  fun writeAck2(ackSequence: Int, socket: SrtSocket?) {
    val ack2 = Ack2(ackSequence)
    ack2.write(getTs(), socketId)
    socket?.write(ack2)
  }

  @Throws(IOException::class)
  fun writeShutdown(socket: SrtSocket?) {
    val shutdown = Shutdown()
    shutdown.write(getTs(), socketId)
    socket?.write(shutdown)
  }
}