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
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress

/**
 * Created by pedro on 22/9/24.
 */
class UdpStreamSocket(
  private val host: String,
  private val port: Int,
  private val sourcePort: Int? = null,
  private val receiveSize: Int? = null,
  private val broadcastMode: Boolean = false
): StreamSocket {

  private val address = InetSocketAddress(host, port)
  private var selectorManager = SelectorManager(Dispatchers.IO)
  private var socket: ConnectedDatagramSocket? = null
  private var myAddress: InetAddress? = null

  override suspend fun connect() {
    selectorManager = SelectorManager(Dispatchers.IO)
    val builder = aSocket(selectorManager).udp()
    val localAddress = if (sourcePort == null) null else InetSocketAddress("0.0.0.0", sourcePort)
    val socket = builder.connect(
      remoteAddress = address,
      localAddress = localAddress
    ) {
      broadcast = broadcastMode
      receiveBufferSize = receiveSize ?: 0
    }
    myAddress = java.net.InetSocketAddress(host, port).address
    this.socket = socket
  }

  override suspend fun close() = withContext(Dispatchers.IO) {
    try {
      socket?.close()
      selectorManager.close()
    } catch (ignored: Exception) {}
  }

  override fun isConnected(): Boolean = socket?.isClosed != true

  override fun isReachable(): Boolean = myAddress?.isReachable(5000) ?: false

  suspend fun readPacket(): ByteArray {
    val socket = socket ?: throw ConnectException("Read with socket closed, broken pipe")
    val packet = socket.receive().packet
    val length = packet.remaining.toInt()
    return packet.readBytes().sliceArray(0 until length)
  }

  suspend fun writePacket(bytes: ByteArray) {
    val datagram = Datagram(ByteReadPacket(bytes), address)
    socket?.send(datagram)
  }
}