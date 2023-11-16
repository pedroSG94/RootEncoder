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

package com.pedro.rtsp.rtp.sockets

import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 7/11/18.
 */
class RtpSocketTcp : BaseRtpSocket() {

  private var outputStream: OutputStream? = null
  private val tcpHeader: ByteArray = byteArrayOf('$'.code.toByte(), 0, 0, 0)

  @Throws(IOException::class)
  override fun setDataStream(outputStream: OutputStream, host: String) {
    this.outputStream = outputStream
  }

  @Throws(IOException::class)
  override suspend fun sendFrame(rtpFrame: RtpFrame, isEnableLogs: Boolean) {
    sendFrameTCP(rtpFrame, isEnableLogs)
  }

  override fun close() {}

  @Throws(IOException::class)
  private fun sendFrameTCP(rtpFrame: RtpFrame, isEnableLogs: Boolean) {
    synchronized(RtpConstants.lock) {
      val len = rtpFrame.length
      tcpHeader[1] = (2 * rtpFrame.channelIdentifier).toByte()
      tcpHeader[2] = (len shr 8).toByte()
      tcpHeader[3] = (len and 0xFF).toByte()
      outputStream?.write(tcpHeader)
      outputStream?.write(rtpFrame.buffer, 0, len)
      outputStream?.flush()
      if (isEnableLogs) {
        Log.i(TAG, "wrote packet: ${(if (rtpFrame.isVideoFrame()) "Video" else "Audio")}, size: ${len + tcpHeader.size}")
      }
    }
  }
}