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

package com.pedro.rtmp

import com.pedro.common.readUInt16
import com.pedro.common.readUInt24
import com.pedro.common.readUInt32
import com.pedro.common.readUInt32LittleEndian
import com.pedro.common.readUntil
import com.pedro.common.writeUInt16
import com.pedro.common.writeUInt24
import com.pedro.common.writeUInt32
import com.pedro.common.writeUInt32LittleEndian
import com.pedro.rtmp.utils.socket.RtmpSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 24/9/24.
 */
class FakeRtmpSocket: RtmpSocket() {

  var input = ByteArrayInputStream(byteArrayOf())
    private set
  val output = ByteArrayOutputStream()
  private var connected = false

  fun setInputBytes(byteArray: ByteArray) {
    input = ByteArrayInputStream(byteArray)
  }

  override suspend fun flush(isPacket: Boolean) {
    output.flush()
  }

  override suspend fun connect() {
    connected = true
  }

  override suspend fun close() {
    connected = false
  }

  override fun isConnected(): Boolean = connected

  override fun isReachable(): Boolean = connected

  override suspend fun write(b: Int) {
    output.write(b)
  }

  override suspend fun write(b: ByteArray) {
    output.write(b)
  }

  override suspend fun write(b: ByteArray, offset: Int, size: Int) {
    output.write(b, offset, size)
  }

  override suspend fun writeUInt16(b: Int) {
    output.writeUInt16(b)
  }

  override suspend fun writeUInt24(b: Int) {
    output.writeUInt24(b)
  }

  override suspend fun writeUInt32(b: Int) {
    output.writeUInt32(b)
  }

  override suspend fun writeUInt32LittleEndian(b: Int) {
    output.writeUInt32LittleEndian(b)
  }

  override suspend fun read(): Int = input.read()

  override suspend fun readUInt16(): Int = input.readUInt16()

  override suspend fun readUInt24(): Int = input.readUInt24()

  override suspend fun readUInt32(): Int = input.readUInt32()

  override suspend fun readUInt32LittleEndian(): Int = input.readUInt32LittleEndian()

  override suspend fun readUntil(b: ByteArray) = input.readUntil(b)
}