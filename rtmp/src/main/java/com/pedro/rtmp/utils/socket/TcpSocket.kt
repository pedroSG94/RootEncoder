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

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.net.ssl.TrustManager
import kotlin.coroutines.coroutineContext

/**
 * Created by pedro on 5/4/22.
 */
class TcpSocket(
  private val host: String,
  private val port: Int,
  private val secured: Boolean,
  private val certificates: Array<TrustManager>?
): RtmpSocket() {

  private var selectorManager = SelectorManager(Dispatchers.IO)
  private var socket: Socket? = null
  private var input: ByteReadChannel? = null
  private var output: ByteWriteChannel? = null

  override suspend fun flush(isPacket: Boolean) {
    output?.flush()
  }

  override suspend fun connect() {
    selectorManager = SelectorManager(Dispatchers.IO)
    val builder = aSocket(selectorManager).tcp()
    val socket = if (secured) {
      builder.connect(host, port) { socketTimeout = timeout }.tls(
        coroutineContext = coroutineContext
      ) {
        //TODO certificates
      }
    } else {
      builder.connect(host, port) { socketTimeout = timeout }
    }
    this.socket = socket
    input = socket.openReadChannel()
    output = socket.openWriteChannel(autoFlush = false)
  }

  override suspend fun close() = withContext(Dispatchers.IO) {
    try {
      input = null
      output = null
      socket?.close()
      selectorManager.close()
    } catch (ignored: Exception) {}
  }

  override fun isConnected(): Boolean = socket?.isClosed != true

  override fun isReachable(): Boolean = socket?.isActive == true
  override suspend fun write(b: Int) {
    output?.writeByte(b)
  }

  override suspend fun write(b: ByteArray) {
    output?.writeFully(b)
  }

  override suspend fun write(b: ByteArray, offset: Int, size: Int) {
    output?.writeFully(b, offset, size)
  }

  override suspend fun writeUInt16(b: Int) {
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  override suspend fun writeUInt24(b: Int) {
    output?.writeByte(b ushr 16)
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  override suspend fun writeUInt32(b: Int) {
    output?.writeByte(b ushr 24)
    output?.writeByte(b ushr 16)
    output?.writeByte(b ushr 8)
    output?.writeByte(b)
  }

  override suspend fun writeUInt32LittleEndian(b: Int) {
    output?.writeByte(b)
    output?.writeByte(b ushr 8)
    output?.writeByte(b ushr 16)
    output?.writeByte(b ushr 24)
  }

  override suspend fun read(): Int {
    return input?.readByte()?.toInt() ?: throw IOException("read with socket closed")
  }

  override suspend fun readUInt16(): Int {
    return read() and 0xff shl 8 or (read() and 0xff)
  }

  override suspend fun readUInt24(): Int {
    return read() and 0xff shl 16 or (read() and 0xff shl 8) or (read() and 0xff)
  }

  override suspend fun readUInt32(): Int {
    return read() and 0xff shl 24 or (read() and 0xff shl 16) or (read() and 0xff shl 8) or (read() and 0xff)
  }

  override suspend fun readUInt32LittleEndian(): Int {
    return Integer.reverseBytes(readUInt32())
  }

  override suspend fun readUntil(b: ByteArray) {
    return input?.readFully(b) ?: throw IOException("read with socket closed")
  }
}