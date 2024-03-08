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

package com.pedro.udp.utils

import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * Created by pedro on 6/3/24.
 */
class UdpSocket(private val host: String, private val type: UdpType, private val port: Int) {

  private var socket: DatagramSocket? = null
  private var packetSize = MpegTsPacketizer.packetSize
  private val timeout = 5000

  fun connect() {
    val address = InetAddress.getByName(host)
    socket = when (type) {
      UdpType.UNICAST -> DatagramSocket()
      UdpType.MULTICAST -> MulticastSocket()
      UdpType.BROADCAST -> DatagramSocket().apply { broadcast = true }
    }
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

  fun write(mpegTsPacket: MpegTsPacket): Int {
    val buffer = mpegTsPacket.buffer
    val udpPacket = DatagramPacket(buffer, buffer.size)
    socket?.send(udpPacket)
    return buffer.size
  }

  fun readBuffer(): ByteArray {
    val buffer = ByteArray(packetSize)
    val udpPacket = DatagramPacket(buffer, buffer.size)
    socket?.receive(udpPacket)
    return udpPacket.data.sliceArray(0 until udpPacket.length)
  }
}