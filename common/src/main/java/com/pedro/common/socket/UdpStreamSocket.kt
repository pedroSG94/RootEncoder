/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.common.socket

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ConnectedDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ReadWriteSocket
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes

/**
 * Created by pedro on 22/9/24.
 */
class UdpStreamSocket(
  host: String,
  port: Int,
  private val sourcePort: Int? = null,
  private val receiveSize: Int? = null,
  private val broadcastMode: Boolean = false
): StreamSocket(host, port) {

  private val address = InetSocketAddress(host, port)
  private val udpSocket by lazy {
    socket as ConnectedDatagramSocket
  }

  override suspend fun buildSocketConfigAndConnect(selectorManager: SelectorManager): ReadWriteSocket {
    val builder = aSocket(selectorManager).udp()
    val localAddress = if (sourcePort == null) null else InetSocketAddress("0.0.0.0", sourcePort)
    return builder.connect(
      remoteAddress = address,
      localAddress = localAddress
    ) {
      broadcast = broadcastMode
      receiveBufferSize = receiveSize ?: 0
    }
  }

  override suspend fun closeResources() { }

  suspend fun readPacket(): ByteArray {
    val packet = udpSocket.receive().packet
    val length = packet.remaining.toInt()
    return packet.readBytes().sliceArray(0 until length)
  }

  suspend fun writePacket(bytes: ByteArray) {
    val datagram = Datagram(ByteReadPacket(bytes), address)
    udpSocket.send(datagram)
  }
}