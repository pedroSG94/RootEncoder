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

import com.pedro.srt.srt.packets.SrtPacket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Created by pedro on 22/8/23.
 */
class SrtSocket(private val host: String, private val port: Int) {

  private val TAG = "SrtSocket"
  private var socket: DatagramSocket? = null
  private var packetSize = Constants.MTU
  private val timeout = 5000

  fun connect() {
    val address = InetAddress.getByName(host)
    socket = DatagramSocket()
    socket?.connect(address, port)
    socket?.soTimeout = timeout
  }

  fun close() {
    if (socket?.isClosed == false) {
      socket?.disconnect()
      socket?.close()
      socket = null
    }
  }

  fun isConnected(): Boolean {
    return socket?.isConnected ?: false
  }

  fun isReachable(): Boolean {
    return socket?.inetAddress?.isReachable(5000) ?: false
  }

  fun setPacketSize(size: Int) {
    packetSize = size
  }

  fun write(srtPacket: SrtPacket) {
    val buffer = srtPacket.getData()
    val udpPacket = DatagramPacket(buffer, buffer.size)
    socket?.send(udpPacket)
  }

  fun readBuffer(): ByteArray {
    val buffer = ByteArray(packetSize)
    val udpPacket = DatagramPacket(buffer, buffer.size)
    socket?.receive(udpPacket)
    return udpPacket.data.sliceArray(0 until udpPacket.length)
  }
}