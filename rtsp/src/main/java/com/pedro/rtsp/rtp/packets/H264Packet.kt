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

package com.pedro.rtsp.rtp.packets

import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.nal.NalReader
import com.pedro.common.removeInfo
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.getData
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3984
 */
class H264Packet: BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + RtpConstants.trackVideo
) {

  private var sps: ByteBuffer? = null
  private var pps: ByteBuffer? = null

  init {
    channelIdentifier = RtpConstants.trackVideo
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {
    this.sps = ByteBuffer.wrap(sps.getData())
    this.pps = ByteBuffer.wrap(pps.getData())
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    fixedBuffer.rewind()
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H264, false)
    if (nals.isEmpty()) return

    val ts = mediaFrame.info.timestamp * 1000L
    val frames = mutableListOf<RtpFrame>()
    if (mediaFrame.info.isKeyFrame) {
      val sps = this.sps
      val pps = this.pps
      if (sps != null && pps != null) {
        if (!nals.contains(pps)) nals.add(0, pps.duplicate())
        if (!nals.contains(sps)) nals.add(0, sps.duplicate())
      }
    }
    nals.forEachIndexed { index, data ->
      val nalType = data.get()
      val nalSize = data.remaining()
      // Small NAL unit => Single NAL unit
      if (nalSize <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1 - encryptSize()) {
        val buffer = getBuffer(nalSize + RtpConstants.RTP_HEADER_LENGTH + 1 + encryptSize())
        buffer[RtpConstants.RTP_HEADER_LENGTH] = nalType
        data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, nalSize)
        val rtpTs = updateTimeStamp(buffer, ts)
        if (index == nals.size - 1) markPacket(buffer) //mark end frame
        updateSeq(buffer)
        encryptPacket(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
        frames.add(rtpFrame)
      } else {
        // Set FU-A header
        val fuHeader = nalType and 0x1F // FU header type
        // Set FU-A indicator
        val fuIndicator = (nalType and 0x60).plus(28).toByte() // FU indicator NRI

        var sum = 0
        while (sum < nalSize) {
          val length = if (nalSize - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 - encryptSize()) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 - encryptSize()
          } else {
            data.remaining()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2 + encryptSize())
          buffer[RtpConstants.RTP_HEADER_LENGTH] = fuIndicator
          // Switch start bit
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = if (sum > 0) fuHeader else (fuHeader or 0x80.toByte())
          val rtpTs = updateTimeStamp(buffer, ts)
          data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length)
          sum += length
          // Last packet before next NAL
          if (sum >= nalSize) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = buffer[RtpConstants.RTP_HEADER_LENGTH + 1].plus(0x40).toByte()
            if (index == nals.size - 1) markPacket(buffer) //mark end frame
          }
          updateSeq(buffer)
          encryptPacket(buffer)
          val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
          frames.add(rtpFrame)
        }
      }
    }
    if (frames.isNotEmpty()) callback(frames)
  }
}