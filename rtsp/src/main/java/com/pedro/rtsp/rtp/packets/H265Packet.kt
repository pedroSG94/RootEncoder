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
import kotlin.experimental.or

/**
 * Created by pedro on 28/11/18.
 *
 * RFC 7798.
 */
class H265Packet(track: Int): BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + track
) {

  private var sps: ByteBuffer? = null
  private var pps: ByteBuffer? = null
  private var vps: ByteBuffer? = null

  init {
    channelIdentifier = track
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
    this.sps = ByteBuffer.wrap(sps.getData())
    this.pps = ByteBuffer.wrap(pps.getData())
    this.vps = ByteBuffer.wrap(vps.getData())
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H265, false)
    if (nals.isEmpty()) return

    val ts = mediaFrame.info.timestamp * 1000L
    val frames = mutableListOf<RtpFrame>()
    if (mediaFrame.info.isKeyFrame) {
      val sps = this.sps
      val pps = this.pps
      val vps = this.vps
      if (sps != null && pps != null && vps != null) {
        if (!nals.contains(pps)) nals.add(0, pps.duplicate())
        if (!nals.contains(sps)) nals.add(0, sps.duplicate())
        if (!nals.contains(vps)) nals.add(0, vps.duplicate())
      }
    }
    nals.forEachIndexed { index, data ->
      val nalType = data.get()
      val nalType2 = data.get()
      val nalSize = data.remaining()
      // Small NAL unit => Single NAL unit
      if (nalSize <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 - encryptSize()) {
        val buffer = getBuffer(nalSize + RtpConstants.RTP_HEADER_LENGTH + 2 + encryptSize())
        //Set PayloadHdr (exact copy of nal unit header)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = nalType
        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = nalType2
        data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, nalSize)
        val rtpTs = updateTimeStamp(buffer, ts)
        if (index == nals.size - 1) markPacket(buffer) //mark end frame
        updateSeq(buffer)
        encryptPacket(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
        frames.add(rtpFrame)
      } else {
        // Set FU header
        //   +---------------+
        //   |0|1|2|3|4|5|6|7|
        //   +-+-+-+-+-+-+-+-+
        //   |S|E|  FuType   |
        //   +---------------+
        val type: Int = nalType.toInt().shr(1) and 0x3F
        val fuHeader = type.toByte() // FU header type

        var sum = 0
        while (sum < nalSize) {
          val length = if (nalSize - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3 - encryptSize()) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3 - encryptSize()
          } else {
            data.remaining()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 3 + encryptSize())
          //Set PayloadHdr (16bit type=49)
          buffer[RtpConstants.RTP_HEADER_LENGTH] = (49 shl 1).toByte()
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = 1
          // Switch start bit
          buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = if (sum > 0) fuHeader else (fuHeader or 0x80.toByte())
          val rtpTs = updateTimeStamp(buffer, ts)
          data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 3, length)
          sum += length
          // Last packet before next NAL
          if (sum >= nalSize) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = buffer[RtpConstants.RTP_HEADER_LENGTH + 2].plus(0x40).toByte()
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