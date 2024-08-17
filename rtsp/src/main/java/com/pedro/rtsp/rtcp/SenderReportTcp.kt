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

import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 8/11/18.
 */
class SenderReportTcp : BaseSenderReport() {

  private var outputStream: OutputStream? = null
  private val tcpHeader: ByteArray = byteArrayOf('$'.code.toByte(), 0, 0, RtpConstants.REPORT_PACKET_LENGTH.toByte())

  @Throws(IOException::class)
  override fun setDataStream(outputStream: OutputStream, host: String) {
    this.outputStream = outputStream
  }

  @Throws(IOException::class)
  override suspend fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame) {
    sendReportTCP(buffer, rtpFrame.channelIdentifier)
  }

  override fun close() {}

  @Throws(IOException::class)
  private fun sendReportTCP(buffer: ByteArray, channelIdentifier: Int) {
    synchronized(RtpConstants.lock) {
      tcpHeader[1] = (2 * channelIdentifier + 1).toByte()
      outputStream?.write(tcpHeader)
      outputStream?.write(buffer, 0, RtpConstants.REPORT_PACKET_LENGTH)
      outputStream?.flush()
    }
  }
}