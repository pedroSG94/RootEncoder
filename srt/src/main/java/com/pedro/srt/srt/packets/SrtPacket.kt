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

package com.pedro.srt.srt.packets

import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.Handshake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by pedro on 21/8/23.
 */
abstract class SrtPacket {

  val buffer = ByteArrayOutputStream()

  companion object {

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
            ControlType.KEEP_ALIVE -> TODO()
            ControlType.ACK -> TODO()
            ControlType.NAK -> TODO()
            ControlType.CONGESTION_WARNING -> TODO()
            ControlType.SHUTDOWN -> {
              val shutdown = Shutdown()
              return shutdown
            }
            ControlType.ACK2 -> TODO()
            ControlType.DROP_REQ -> TODO()
            ControlType.PEER_ERROR -> TODO()
            ControlType.USER_DEFINED -> TODO()
            else -> throw IOException("unknown control type: ${type.name}")
          }
        }
      }
    }
  }

  fun getData(): ByteArray = buffer.toByteArray()
}