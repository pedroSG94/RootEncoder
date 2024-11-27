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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import java.net.ConnectException

/**
 * Created by pedro on 22/9/24.
 */
abstract class TcpStreamSocket: StreamSocket {

  protected val timeout = 5000L
  protected var input: ByteReadChannel? = null
  protected var output: ByteWriteChannel? = null
  protected var selectorManager = SelectorManager(Dispatchers.IO)

  suspend fun flush() {
    output?.flush()
  }

  suspend fun write(b: Int) {
    output?.writeByte(b.toByte())
  }

  suspend fun write(b: ByteArray) {
    output?.writeFully(b)
  }

  suspend fun write(b: ByteArray, offset: Int, size: Int) {
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

  suspend fun write(string: String) {
    output?.writeStringUtf8(string)
  }

  suspend fun read(): Int {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    return input.readByte().toInt()
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

  suspend fun readUntil(b: ByteArray) {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    input.readFully(b)
  }

  suspend fun readLine(): String? {
    val input = input ?: throw ConnectException("Read with socket closed, broken pipe")
    return input.readUTF8Line()
  }
}