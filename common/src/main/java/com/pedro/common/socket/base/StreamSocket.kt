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

package com.pedro.common.socket.base

import com.pedro.common.socket.java.TcpStreamSocketJava
import com.pedro.common.socket.java.UdpStreamSocketJava
import com.pedro.common.socket.ktor.TcpStreamSocketKtor
import com.pedro.common.socket.ktor.UdpStreamSocketKtor
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 22/9/24.
 */
abstract class StreamSocket {
  protected val timeout = 5000L
  abstract suspend fun connect()
  abstract suspend fun close()
  abstract fun isConnected(): Boolean
  abstract fun isReachable(): Boolean

  companion object {
    fun createTcpSocket(
      type: SocketType,
      host: String, port: Int, secured: Boolean, certificates: TrustManager? = null
    ): TcpStreamSocket {
      return when (type) {
        SocketType.KTOR -> TcpStreamSocketKtor(host, port, secured, certificates)
        SocketType.JAVA -> TcpStreamSocketJava(host, port, secured, certificates)
      }
    }

    fun createUdpSocket(
      type: SocketType,
      host: String, port: Int, sourcePort: Int? = null, receiveSize: Int? = null, udpType: UdpType = UdpType.UNICAST
    ): UdpStreamSocket {
      return when (type) {
        SocketType.KTOR -> UdpStreamSocketKtor(host, port, sourcePort, receiveSize, udpType)
        SocketType.JAVA -> UdpStreamSocketJava(host, port, sourcePort, receiveSize, udpType)
      }
    }
  }
}