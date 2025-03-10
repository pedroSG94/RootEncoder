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

package com.pedro.srt.utils

import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.srt.srt.packets.SrtPacket

/**
 * Created by pedro on 22/8/23.
 */
class SrtSocket(type: SocketType, host: String, port: Int) {

  private val socket = StreamSocket.createUdpSocket(type, host, port, receiveSize = Constants.MTU)

  suspend fun connect() {
    socket.connect()
  }

  suspend fun close() {
    socket.close()
  }

  fun isConnected() = socket.isConnected()

  fun isReachable() = socket.isReachable()

  suspend fun write(srtPacket: SrtPacket) {
    val buffer = srtPacket.getData()
    socket.write(buffer)
  }

  suspend fun readBuffer() = socket.read()
}