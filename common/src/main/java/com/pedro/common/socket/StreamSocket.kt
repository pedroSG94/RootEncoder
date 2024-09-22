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
import io.ktor.network.sockets.ReadWriteSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Created by pedro on 22/9/24.
 */
abstract class StreamSocket(
  private val host: String,
  private val port: Int
) {

  private var selectorManager = SelectorManager(Dispatchers.IO)
  protected var socket: ReadWriteSocket? = null
  private var address: InetAddress? = null

  abstract suspend fun buildSocketConfigAndConnect(selectorManager: SelectorManager): ReadWriteSocket
  abstract suspend fun closeResources()

  suspend fun connect() {
    selectorManager = SelectorManager(Dispatchers.IO)
    val socket = buildSocketConfigAndConnect(selectorManager)
    address = InetSocketAddress(host, port).address
    this.socket = socket
  }

  suspend fun close() = withContext(Dispatchers.IO) {
    try {
      address = null
      closeResources()
      socket?.close()
      selectorManager.close()
    } catch (ignored: Exception) {}
  }

  fun isConnected(): Boolean = socket?.isClosed != true

  fun isReachable(): Boolean = address?.isReachable(5000) ?: false
}