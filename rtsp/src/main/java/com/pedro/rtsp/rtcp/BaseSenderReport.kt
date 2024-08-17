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

import com.pedro.common.TimeUtils
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.setLong
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 7/11/18.
 */
abstract class BaseSenderReport internal constructor() {

  private val interval: Long = 3000
  private val videoBuffer = ByteArray(RtpConstants.MTU)
  private val audioBuffer = ByteArray(RtpConstants.MTU)
  private var videoTime: Long = 0
  private var audioTime: Long = 0
  private var videoPacketCount = 0L
  private var videoOctetCount = 0L
  private var audioPacketCount = 0L
  private var audioOctetCount = 0L

  companion object {
    @JvmStatic
    fun getInstance(protocol: Protocol, videoSourcePort: Int, audioSourcePort: Int): BaseSenderReport {
      return if (protocol === Protocol.TCP) {
        SenderReportTcp()
      } else {
        SenderReportUdp(videoSourcePort, audioSourcePort)
      }
    }
  }

  init {
    /*							     Version(2)  Padding(0)					 					*/
    /*									 ^		  ^			PT = 0	    						*/
    /*									 |		  |				^								*/
    /*									 | --------			 	|								*/
    /*									 | |---------------------								*/
    /*									 | ||													*/
    /*									 | ||													*/
    videoBuffer[0] = 0x80.toByte()
    audioBuffer[0] = 0x80.toByte()

    /* Packet Type PT */
    videoBuffer[1] = 200.toByte()
    audioBuffer[1] = 200.toByte()

    /* Byte 2,3          ->  Length		                     */
    videoBuffer.setLong(RtpConstants.REPORT_PACKET_LENGTH / 4 - 1L, 2, 4)
    audioBuffer.setLong(RtpConstants.REPORT_PACKET_LENGTH / 4 - 1L, 2, 4)
    /* Byte 4,5,6,7      ->  SSRC                            */
    /* Byte 8,9,10,11    ->  NTP timestamp hb				 */
    /* Byte 12,13,14,15  ->  NTP timestamp lb				 */
    /* Byte 16,17,18,19  ->  RTP timestamp		             */
    /* Byte 20,21,22,23  ->  packet count				 	 */
    /* Byte 24,25,26,27  ->  octet count			         */
  }

  fun setSSRC(ssrcVideo: Long, ssrcAudio: Long) {
    videoBuffer.setLong(ssrcVideo, 4, 8)
    audioBuffer.setLong(ssrcAudio, 4, 8)
  }

  @Throws(IOException::class)
  abstract fun setDataStream(outputStream: OutputStream, host: String)

  @Throws(IOException::class)
  suspend fun update(rtpFrame: RtpFrame): Boolean {
    return if (rtpFrame.channelIdentifier == RtpConstants.trackVideo) {
      updateVideo(rtpFrame)
    } else {
      updateAudio(rtpFrame)
    }
  }

  @Throws(IOException::class)
  abstract suspend fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame)

  @Throws(IOException::class)
  private suspend fun updateVideo(rtpFrame: RtpFrame): Boolean {
    videoPacketCount++
    videoOctetCount += rtpFrame.length
    videoBuffer.setLong(videoPacketCount, 20, 24)
    videoBuffer.setLong(videoOctetCount, 24, 28)
    if (TimeUtils.getCurrentTimeMillis() - videoTime >= interval) {
      videoTime = TimeUtils.getCurrentTimeMillis()
      setData(videoBuffer, TimeUtils.getCurrentTimeNano(), rtpFrame.timeStamp)
      sendReport(videoBuffer, rtpFrame)
      return true
    }
    return false
  }

  @Throws(IOException::class)
  private suspend fun updateAudio(rtpFrame: RtpFrame): Boolean {
    audioPacketCount++
    audioOctetCount += rtpFrame.length
    audioBuffer.setLong(audioPacketCount, 20, 24)
    audioBuffer.setLong(audioOctetCount, 24, 28)
    if (TimeUtils.getCurrentTimeMillis() - audioTime >= interval) {
      audioTime = TimeUtils.getCurrentTimeMillis()
      setData(audioBuffer, TimeUtils.getCurrentTimeNano(), rtpFrame.timeStamp)
      sendReport(audioBuffer, rtpFrame)
      return true
    }
    return false
  }

  fun reset() {
    videoOctetCount = 0
    videoPacketCount = 0
    audioOctetCount = 0
    audioPacketCount = 0
    audioTime = 0
    videoTime = 0
    videoBuffer.setLong(videoPacketCount, 20, 24)
    videoBuffer.setLong(videoOctetCount, 24, 28)
    audioBuffer.setLong(audioPacketCount, 20, 24)
    audioBuffer.setLong(audioOctetCount, 24, 28)
  }

  abstract fun close()

  private fun setData(buffer: ByteArray, ntpts: Long, rtpts: Long) {
    val hb = ntpts / 1000000000
    val lb = (ntpts - hb * 1000000000) * 4294967296L / 1000000000
    buffer.setLong(hb, 8, 12)
    buffer.setLong(lb, 12, 16)
    buffer.setLong(rtpts, 16, 20)
  }
}