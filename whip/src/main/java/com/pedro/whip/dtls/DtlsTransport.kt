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

import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.runBlocking
import org.bouncycastle.tls.DatagramTransport
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Created by pedro on 21/7/25.
 */
class DtlsTransport(
  private val socket: UdpStreamSocket
): DatagramTransport {

  private val queue = LinkedBlockingQueue<ByteArray>()

  override fun getReceiveLimit(): Int {
    return RtpConstants.MTU
  }

  fun enqueue(bytes: ByteArray) {
    queue.offer(bytes)
  }

  override fun receive(buffer: ByteArray, offset: Int, length: Int, waitMillis: Int): Int {
    if (waitMillis <= 0) return -1
    return try {
      val bytes = queue.poll(waitMillis.toLong(), TimeUnit.MILLISECONDS) ?: return -1
      val result = min(length, bytes.size)
      System.arraycopy(bytes, 0, buffer, offset, result)
      result
    } catch (_: InterruptedException) {
      -1
    }
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
    queue.clear()
  }
}
