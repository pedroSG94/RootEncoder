/*
 * Copyright (C) 2023 pedroSG94.
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

import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * Created by pedro on 8/11/18.
 */
class SenderReportUdp(
  videoSourcePort: Int, audioSourcePort: Int,
  private var multicastSocketVideo: MulticastSocket? = null,
  private var multicastSocketAudio: MulticastSocket? = null
) : BaseSenderReport() {

  private val datagramPacket = DatagramPacket(byteArrayOf(0), 1)

  init {
    if (multicastSocketVideo == null) multicastSocketVideo = MulticastSocket(videoSourcePort)
    multicastSocketVideo?.timeToLive = 64
    if (multicastSocketAudio == null) multicastSocketAudio = MulticastSocket(audioSourcePort)
    multicastSocketAudio?.timeToLive = 64
  }

  @Throws(IOException::class)
  override fun setDataStream(outputStream: OutputStream, host: String) {
    datagramPacket.address = InetAddress.getByName(host)
  }

  @Throws(IOException::class)
  override suspend fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame, type: String, packetCount: Long, octetCount: Long, isEnableLogs: Boolean) {
    sendReportUDP(buffer, rtpFrame.rtcpPort, type, packetCount, octetCount, isEnableLogs)
  }

  override fun close() {
    multicastSocketVideo?.close()
    multicastSocketAudio?.close()
  }

  @Throws(IOException::class)
  private fun sendReportUDP(buffer: ByteArray, port: Int, type: String, packet: Long, octet: Long, isEnableLogs: Boolean) {
    synchronized(RtpConstants.lock) {
      datagramPacket.data = buffer
      datagramPacket.port = port
      datagramPacket.length = PACKET_LENGTH
      if (type == "Video") {
        multicastSocketVideo?.send(datagramPacket)
      } else {
        multicastSocketAudio?.send(datagramPacket)
      }
      if (isEnableLogs) {
        Log.i(TAG, "wrote report: $type, port: $port, packets: $packet, octet: $octet")
      }
    }
  }
}