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

package com.pedro.rtsp.rtcp

import com.pedro.common.socket.TcpStreamSocket
import com.pedro.common.socket.UdpStreamSocket
import com.pedro.rtsp.rtsp.RtpFrame
import java.io.IOException

/**
 * Created by pedro on 8/11/18.
 */
class SenderReportUdp(
  private val videoSocket: UdpStreamSocket?,
  private val audioSocket: UdpStreamSocket?,
) : BaseSenderReport() {

  @Throws(IOException::class)
  override suspend fun setSocket(socket: TcpStreamSocket) {
    videoSocket?.connect()
    audioSocket?.connect()
  }

  @Throws(IOException::class)
  override suspend fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame) {
    sendReportUDP(buffer, rtpFrame.isVideoFrame())
  }

  override suspend fun close() {
    videoSocket?.close()
    audioSocket?.close()
  }

  @Throws(IOException::class)
  private suspend fun sendReportUDP(buffer: ByteArray, isVideo: Boolean) {
    if (isVideo) {
      videoSocket?.writePacket(buffer)
    } else {
      audioSocket?.writePacket(buffer)
    }
  }
}