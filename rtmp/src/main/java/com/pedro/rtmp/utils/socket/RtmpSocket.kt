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

/**
 * Created by pedro on 5/4/22.
 * Socket implementation that accept:
 * - TCP
 * - TCP SSL/TLS
 * - UDP
 * - Tunneled HTTP
 * - Tunneled HTTPS
 */
abstract class RtmpSocket {

  protected val timeout = 5000L

  abstract suspend fun flush(isPacket: Boolean = false)
  abstract suspend fun connect()
  abstract suspend fun close()
  abstract fun isConnected(): Boolean
  abstract fun isReachable(): Boolean

  abstract suspend fun write(b: Int)
  abstract suspend fun writeUInt16(b: Int)
  abstract suspend fun writeUInt24(b: Int)
  abstract suspend fun writeUInt32(b: Int)
  abstract suspend fun writeUInt32LittleEndian(b: Int)
  abstract suspend fun write(b: ByteArray)
  abstract suspend fun write(b: ByteArray, offset: Int, size: Int)
  abstract suspend fun read(): Int
  abstract suspend fun readUInt16(): Int
  abstract suspend fun readUInt24(): Int
  abstract suspend fun readUInt32(): Int
  abstract suspend fun readUInt32LittleEndian(): Int
  abstract suspend fun readUntil(b: ByteArray)
}