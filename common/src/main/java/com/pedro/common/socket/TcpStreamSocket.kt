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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.security.SecureRandom
import javax.net.ssl.TrustManager

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
    val builder = aSocket(selectorManager).tcp().connect(remoteAddress = InetSocketAddress(host, port))
    val socket = if (secured) {
      builder.tls(Dispatchers.Default) {
        trustManager = certificate
        random = SecureRandom()
      }
    } else builder
    input = socket.openReadChannel()
    output = socket.openWriteChannel(autoFlush = false)
    return socket
  }

  override suspend fun closeResources() {
    input = null
    output = null
  }

  suspend fun flush() = withTimeout(timeout) {
    output?.flush()
  }

  suspend fun write(b: Int) = withTimeout(timeout) {
    output?.writeByte(b)
  }

  suspend fun write(b: ByteArray) = withTimeout(timeout) {
    output?.writeFully(b)
  }

  suspend fun write(b: ByteArray, offset: Int, size: Int) = withTimeout(timeout) {
    output?.writeFully(b, offset, size)
  }

  suspend fun writeUInt16(b: Int) {
    write(byteArrayOf((b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt24(b: Int) {
    write(byteArrayOf((b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt32(b: Int) {
    write(byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt32LittleEndian(b: Int) {
    writeUInt32(Integer.reverseBytes(b))
  }

  suspend fun write(string: String) = withTimeout(timeout) {
    output?.writeStringUtf8(string)
  }

  suspend fun read(): Int = withTimeout(timeout) {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    input.readByte().toInt()
  }

  suspend fun readUInt16(): Int {
    val b = ByteArray(2)
    readUntil(b)
    return b[0].toInt() and 0xff shl 8 or (b[1].toInt() and 0xff)
  }

  suspend fun readUInt24(): Int {
    val b = ByteArray(3)
    readUntil(b)
    return b[0].toInt() and 0xff shl 16 or (b[1].toInt() and 0xff shl 8) or (b[2].toInt() and 0xff)
  }

  suspend fun readUInt32(): Int {
    val b = ByteArray(4)
    readUntil(b)
    return b[0].toInt() and 0xff shl 24 or (b[1].toInt() and 0xff shl 16) or (b[2].toInt() and 0xff shl 8) or (b[3].toInt() and 0xff)
  }

  suspend fun readUInt32LittleEndian(): Int {
    return Integer.reverseBytes(readUInt32())
  }

  suspend fun readUntil(b: ByteArray) = withTimeout(timeout) {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    input.readFully(b)
  }

  suspend fun readLine(): String? = withTimeout(timeout) {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    input.readUTF8Line()
  }
}