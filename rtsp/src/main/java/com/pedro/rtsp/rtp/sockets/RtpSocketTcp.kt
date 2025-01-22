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

package com.pedro.rtsp.rtp.sockets

import com.pedro.common.socket.TcpStreamSocket
import com.pedro.rtsp.rtsp.RtpFrame
import java.io.IOException

/**
 * Created by pedro on 7/11/18.
 */
class RtpSocketTcp : BaseRtpSocket() {

  private var socket: TcpStreamSocket? = null
  private val tcpHeader: ByteArray = byteArrayOf('$'.code.toByte(), 0, 0, 0)

  @Throws(IOException::class)
  override suspend fun setSocket(socket: TcpStreamSocket) {
    this.socket = socket
  }

  @Throws(IOException::class)
  override suspend fun sendFrame(rtpFrame: RtpFrame) {
    sendFrameTCP(rtpFrame)
  }

  override suspend fun flush() {
    socket?.flush()
  }

  override suspend fun close() {}

  @Throws(IOException::class)
  private suspend fun sendFrameTCP(rtpFrame: RtpFrame) {
    val len = rtpFrame.length
    tcpHeader[1] = (2 * rtpFrame.channelIdentifier).toByte()
    tcpHeader[2] = (len shr 8).toByte()
    tcpHeader[3] = (len and 0xFF).toByte()
    socket?.write(tcpHeader)
    socket?.write(rtpFrame.buffer, 0, len)
  }
}