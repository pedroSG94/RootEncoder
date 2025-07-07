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

import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.toUInt16
import com.pedro.common.toUInt24
import com.pedro.common.toUInt32
import com.pedro.common.toUInt32LittleEndian
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 5/4/22.
 */
class TcpSocket(
  type: SocketType,
  host: String, port: Int, secured: Boolean, certificates: TrustManager?
): RtmpSocket() {

  private val socket = StreamSocket.createTcpSocket(type, host, port, secured, certificates)

  override suspend fun flush(isPacket: Boolean) {
    socket.flush()
  }

  override suspend fun connect() {
    socket.connect()
  }

  override suspend fun close() {
    socket.close()
  }

  override fun isConnected(): Boolean = socket.isConnected()

  override fun isReachable(): Boolean = socket.isReachable()

  override suspend fun write(b: Int) {
    socket.write(b)
  }

  override suspend fun write(b: ByteArray) {
    socket.write(b)
  }

  override suspend fun write(b: ByteArray, offset: Int, size: Int) {
    socket.write(b, offset, size)
  }

  override suspend fun writeUInt16(b: Int) {
    socket.write(b.toUInt16())
  }

  override suspend fun writeUInt24(b: Int) {
    socket.write(b.toUInt24())
  }

  override suspend fun writeUInt32(b: Int) {
    socket.write(b.toUInt32())
  }

  override suspend fun writeUInt32LittleEndian(b: Int) {
    socket.write(b.toUInt32LittleEndian())
  }

  override suspend fun read(): Int = socket.read(1)[0].toInt()

  override suspend fun readUInt16(): Int = socket.read(2).toUInt16()

  override suspend fun readUInt24(): Int = socket.read(3).toUInt24()

  override suspend fun readUInt32(): Int = socket.read(4).toUInt32()

  override suspend fun readUInt32LittleEndian(): Int = socket.read(4).toUInt32LittleEndian()

  override suspend fun readUntil(b: ByteArray) {
    socket.read(b)
  }
}