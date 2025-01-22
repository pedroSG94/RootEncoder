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

package com.pedro.udp.utils

import com.pedro.common.socket.UdpStreamSocket
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer

/**
 * Created by pedro on 6/3/24.
 */
class UdpSocket(host: String, type: UdpType, port: Int) {

  private val socket = UdpStreamSocket(
    host, port, receiveSize = MpegTsPacketizer.packetSize,
    broadcastMode = type == UdpType.BROADCAST
  )

  suspend fun connect() {
    socket.connect()
  }

  suspend fun close() {
    socket.close()
  }

  suspend fun isConnected() = socket.isConnected()

  fun isReachable() = socket.isReachable()

  suspend fun write(mpegTsPacket: MpegTsPacket): Int {
    val buffer = mpegTsPacket.buffer
    socket.writePacket(buffer)
    return buffer.size
  }

  suspend fun readBuffer() = socket.readPacket()
}