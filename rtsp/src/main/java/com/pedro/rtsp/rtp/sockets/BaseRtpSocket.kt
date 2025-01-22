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
import com.pedro.common.socket.UdpStreamSocket
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException

/**
 * Created by pedro on 7/11/18.
 */
abstract class BaseRtpSocket {

  companion object {
    @JvmStatic
    fun getInstance(
      protocol: Protocol, host: String,
      videoSourcePort: Int?, audioSourcePort: Int?,
      videoServerPort: Int?, audioServerPort: Int?,
    ): BaseRtpSocket {
      return if (protocol === Protocol.TCP) {
        RtpSocketTcp()
      } else {
        val videoSocket = if (videoServerPort != null) {
          UdpStreamSocket(
            host, videoServerPort, videoSourcePort, receiveSize = RtpConstants.REPORT_PACKET_LENGTH
          )
        } else null
        val audioSocket = if (audioServerPort != null) {
          UdpStreamSocket(
            host, audioServerPort, audioSourcePort, receiveSize = RtpConstants.REPORT_PACKET_LENGTH
          )
        } else null
        RtpSocketUdp(videoSocket, audioSocket)
      }
    }
  }

  @Throws(IOException::class)
  abstract suspend fun setSocket(socket: TcpStreamSocket)

  @Throws(IOException::class)
  abstract suspend fun sendFrame(rtpFrame: RtpFrame)

  abstract suspend fun flush()

  abstract suspend fun close()
}