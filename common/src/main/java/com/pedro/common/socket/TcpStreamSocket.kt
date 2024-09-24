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
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import java.net.ConnectException
import java.security.SecureRandom
import javax.net.ssl.TrustManager
import kotlin.coroutines.coroutineContext

/**
 * Created by pedro on 22/9/24.
 */
class TcpStreamSocket(
  private val host: String,
  private val port: Int,
  private val secured: Boolean = false,
  private val certificate: TrustManager? = null
): StreamSocket(host, port) {

  private val timeout = 5000L
  private var input: ByteReadChannel? = null
  private var output: ByteWriteChannel? = null

  override suspend fun buildSocketConfigAndConnect(selectorManager: SelectorManager): ReadWriteSocket {
    val builder = aSocket(selectorManager).tcp()
    val socket = if (secured) {
      builder.connect(remoteAddress = InetSocketAddress(host, port)) { socketTimeout = timeout }.tls(
        coroutineContext = coroutineContext
      ) {
        trustManager = certificate
        random = SecureRandom()
      }
    } else {
      builder.connect(host, port) { socketTimeout = timeout }
    }
    input = socket.openReadChannel()
    output = socket.openWriteChannel(autoFlush = false)
    return socket
  }

  override suspend fun closeResources() {
    input = null
    output = null
  }

  suspend fun flush() {
    output?.flush()
  }

  suspend fun write(b: Int) {
    output?.writeByte(b)
  }

  suspend fun write(b: ByteArray) {
    output?.writeFully(b)
  }

  suspend fun write(b: ByteArray, offset: Int, size: Int) {
    output?.writeFully(b, offset, size)
  }

  suspend fun writeUInt16(b: Int) {
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  suspend fun writeUInt24(b: Int) {
    output?.writeByte(b ushr 16)
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  suspend fun writeUInt32(b: Int) {
    output?.writeByte(b ushr 24)
    output?.writeByte(b ushr 16)
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  suspend fun writeUInt32LittleEndian(b: Int) {
    writeUInt32(Integer.reverseBytes(b))
  }

  suspend fun read(): Int {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    return input.readByte().toInt()
  }

  suspend fun readUInt16(): Int {
    return read() and 0xff shl 8 or (read() and 0xff)
  }

  suspend fun readUInt24(): Int {
    return read() and 0xff shl 16 or (read() and 0xff shl 8) or (read() and 0xff)
  }

  suspend fun readUInt32(): Int {
    return read() and 0xff shl 24 or (read() and 0xff shl 16) or (read() and 0xff shl 8) or (read() and 0xff)
  }

  suspend fun readUInt32LittleEndian(): Int {
    return Integer.reverseBytes(readUInt32())
  }

  suspend fun readUntil(b: ByteArray) {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    return input.readFully(b)
  }

  suspend fun readLine(): String? {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    return input.readUTF8Line()
  }

  suspend fun write(string: String) {
    output?.writeStringUtf8(string)
  }
}