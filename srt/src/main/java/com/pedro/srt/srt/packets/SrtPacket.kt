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

import com.pedro.srt.srt.packets.control.Ack
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.CongestionWarning
import com.pedro.srt.srt.packets.control.ControlType
import com.pedro.srt.srt.packets.control.DropReq
import com.pedro.srt.srt.packets.control.KeepAlive
import com.pedro.srt.srt.packets.control.Nak
import com.pedro.srt.srt.packets.control.PeerError
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.Handshake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by pedro on 21/8/23.
 */
abstract class SrtPacket {

  var buffer = ByteArrayOutputStream()

  companion object {

    const val headerSize = 16

    @Throws(IOException::class)
    fun getSrtPacket(buffer: ByteArray): SrtPacket {
      val packetType = PacketType.from((buffer[0].toInt() ushr 7) and 0x01)
      when (packetType) {
        PacketType.DATA -> {
          return DataPacket()
        }
        PacketType.CONTROL -> {
          val headerData = buffer.sliceArray(0 until 4)
          val type = ControlPacket.getType(ByteArrayInputStream(headerData))
          val input = ByteArrayInputStream(buffer)
          when (type) {
            ControlType.HANDSHAKE -> {
              val handshake = Handshake()
              handshake.read(input)
              return handshake
            }
            ControlType.KEEP_ALIVE -> {
              val keepAlive = KeepAlive()
              keepAlive.read(input)
              return keepAlive
            }
            ControlType.ACK -> {
              val ack = Ack()
              ack.read(input)
              return ack
            }
            ControlType.NAK -> {
              val nak = Nak()
              nak.read(input)
              return nak
            }
            ControlType.CONGESTION_WARNING -> {
              val congestionWarning = CongestionWarning()
              congestionWarning.read(input)
              return congestionWarning
            }
            ControlType.SHUTDOWN -> {
              val shutdown = Shutdown()
              shutdown.read(input)
              return shutdown
            }
            ControlType.ACK2 -> {
              val ack2 = Ack2()
              ack2.read(input)
              return ack2
            }
            ControlType.DROP_REQ -> {
              val dropReq = DropReq()
              dropReq.read(input)
              return dropReq
            }
            ControlType.PEER_ERROR -> {
              val peerError = PeerError()
              peerError.read(input)
              return peerError
            }
            ControlType.USER_DEFINED -> throw IOException("user defined type is not allowed")
            else -> throw IOException("unknown control type: ${type.name}")
          }
        }
      }
    }
  }

  fun getData(): ByteArray = buffer.toByteArray()

  fun resetBuffer() {
    buffer = ByteArrayOutputStream()
  }
}