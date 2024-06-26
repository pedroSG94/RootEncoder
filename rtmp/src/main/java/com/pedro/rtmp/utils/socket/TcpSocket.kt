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

package com.pedro.rtmp.utils.socket

import com.pedro.common.TLSSocketFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.security.GeneralSecurityException
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 5/4/22.
 */
class TcpSocket(
  private val host: String,
  private val port: Int,
  private val secured: Boolean,
  private val certificates: Array<TrustManager>?
): RtmpSocket() {

  private var socket: Socket = Socket()
  private var input = ByteArrayInputStream(byteArrayOf()).buffered()
  private var output = ByteArrayOutputStream().buffered()

  override fun getOutStream(): OutputStream = output

  override fun getInputStream(): InputStream = input

  override fun flush(isPacket: Boolean) {
    getOutStream().flush()
  }

  override fun connect() {
    if (secured) {
      try {
        val socketFactory = TLSSocketFactory(certificates)
        socket = socketFactory.createSocket(host, port)
      } catch (e: GeneralSecurityException) {
        throw IOException("Create SSL socket failed: ${e.message}")
      }
    } else {
      socket = Socket()
      val socketAddress: SocketAddress = InetSocketAddress(host, port)
      socket.connect(socketAddress, timeout)
    }
    output = socket.getOutputStream().buffered()
    input = socket.getInputStream().buffered()
    socket.soTimeout = timeout
  }

  override fun close() {
    try {
      if (socket.isConnected) {
        socket.getInputStream().close()
        input.close()
        output.close()
        socket.close()
      }
    } catch (ignored: Exception) {}
  }

  override fun isConnected(): Boolean = socket.isConnected

  override fun isReachable(): Boolean = socket.inetAddress?.isReachable(5000) ?: false
}