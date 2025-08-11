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

package com.pedro.whip.dtls

import android.util.Log
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.trySend
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.runBlocking
import org.bouncycastle.tls.DatagramTransport
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Created by pedro on 21/7/25.
 */
class DtlsTransport(
  private val socket: UdpStreamSocket
): DatagramTransport {

  private var canTransport = false
  private val queue = LinkedBlockingQueue<ByteArray>()

  fun open() {
    canTransport = true
  }

  override fun getReceiveLimit(): Int {
    return RtpConstants.MTU
  }

  fun enqueue(bytes: ByteArray) {
    queue.trySend(bytes)
  }

  override fun receive(buffer: ByteArray, offset: Int, length: Int, waitMillis: Int): Int {
    var readSize = 0
    runBlocking {
      val bytes = socket.read()
      if (bytes[0] in 20..63) {
        readSize = min(length, bytes.size)
        System.arraycopy(bytes, 0, buffer, offset, readSize)
      }
    }
    return readSize
  }

  override fun getSendLimit(): Int {
    return RtpConstants.MTU
  }

  override fun send(buffer: ByteArray, offset: Int, length: Int) {
    runBlocking {
      socket.write(buffer.sliceArray(offset until length))
    }
  }

  override fun close() {
    canTransport = false
  }
}