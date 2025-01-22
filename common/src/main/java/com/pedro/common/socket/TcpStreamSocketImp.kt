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
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ReadWriteSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.security.SecureRandom
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 22/9/24.
 */
class TcpStreamSocketImp(
  private val host: String,
  private val port: Int,
  private val secured: Boolean = false,
  private val certificate: TrustManager? = null
): TcpStreamSocket() {

  private var socket: ReadWriteSocket? = null
  private var address: InetAddress? = null

  override suspend fun connect() {
    selectorManager = SelectorManager(Dispatchers.IO)
    val builder = aSocket(selectorManager).tcp().connect(
      remoteAddress = InetSocketAddress(host, port),
      configure = {
        if (!secured) socketTimeout = timeout
      }
    )
    val socket = if (secured) {
      builder.tls(Dispatchers.Default) {
        trustManager = certificate
        random = SecureRandom()
      }
    } else builder
    input = socket.openReadChannel()
    output = socket.openWriteChannel(autoFlush = false)
    address = java.net.InetSocketAddress(host, port).address
    this.socket = socket
  }

  override suspend fun close() = withContext(Dispatchers.IO) {
    try {
      address = null
      input = null
      output = null
      socket?.close()
      selectorManager.close()
    } catch (ignored: Exception) {}
  }

  override fun isConnected(): Boolean = socket?.isClosed != true

  override fun isReachable(): Boolean = address?.isReachable(5000) ?: false
}