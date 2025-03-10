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

import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.rtsp.RtpFrame
import java.io.IOException

/**
 * Created by pedro on 7/11/18.
 */
class RtpSocketUdp(
  private val videoSocket: UdpStreamSocket?,
  private val audioSocket: UdpStreamSocket?,
) : BaseRtpSocket() {

  @Throws(IOException::class)
  override suspend fun setSocket(socket: TcpStreamSocket) {
    videoSocket?.connect()
    audioSocket?.connect()
  }

  @Throws(IOException::class)
  override suspend fun sendFrame(rtpFrame: RtpFrame) {
    sendFrameUDP(rtpFrame)
  }

  override suspend fun flush() { }

  override suspend fun close() {
    videoSocket?.close()
    audioSocket?.close()
  }

  @Throws(IOException::class)
  private suspend fun sendFrameUDP(rtpFrame: RtpFrame) {
    if (rtpFrame.isVideoFrame()) {
      videoSocket?.write(rtpFrame.buffer)
    } else {
      audioSocket?.write(rtpFrame.buffer)
    }
  }
}