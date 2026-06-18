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

import android.util.Log
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.nal.NalReader
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.getVideoStartCodeSize
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3984
 */
class H264Packet: BasePacket(RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + RtpConstants.trackVideo
) {

  private var stapA: ByteArray? = null
  private var sendKeyFrame = false
  private var videoInfo: Set<ByteBuffer>? = null
  private val header = ByteArray(2)

  init {
    channelIdentifier = RtpConstants.trackVideo
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {
    videoInfo = setOf(sps, pps)
    setSpsPps(sps.toByteArray(), pps.toByteArray())
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val videoInfo = this.videoInfo
    if (videoInfo == null) {
      Log.e(TAG, "waiting for a valid sps and pps")
      return
    }
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    fixedBuffer.rewind()
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H264, true)
    if (nals.isEmpty()) return

    val ts = mediaFrame.info.timestamp * 1000L
    val frames = mutableListOf<RtpFrame>()
    val nalType = nals[0].get(0)
    val type: Int = (nalType and 0x1F).toInt()
    if (type == RtpConstants.IDR || mediaFrame.info.isKeyFrame) {
      stapA?.let {
        val buffer = getBuffer(it.size + RtpConstants.RTP_HEADER_LENGTH)
        val rtpTs = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        System.arraycopy(it, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, it.size)
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, it.size + RtpConstants.RTP_HEADER_LENGTH, channelIdentifier)
        frames.add(rtpFrame)
        sendKeyFrame = true
      } ?: run {
        Log.i(TAG, "can't create key frame because setSpsPps was not called")
      }
    }
    if (sendKeyFrame) {
        nals.forEachIndexed { index, data ->
          val nalType = data.get()
          val nalSize = data.remaining()
          // Small NAL unit => Single NAL unit
          if (nalSize <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1) {
            val buffer = getBuffer(nalSize + RtpConstants.RTP_HEADER_LENGTH + 1)
            buffer[RtpConstants.RTP_HEADER_LENGTH] = nalType
            data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, nalSize)
            val rtpTs = updateTimeStamp(buffer, ts)
            markPacket(buffer) //mark end frame
            updateSeq(buffer)
            val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
            frames.add(rtpFrame)
          } else {
            // Set FU-A header
            header[1] = nalType and 0x1F // FU header type
            header[1] = header[1].plus(0x80).toByte()  // set start bit to 1
            // Set FU-A indicator
            header[0] = nalType and 0x60 and 0xFF.toByte() // FU indicator NRI
            header[0] = header[0].plus(28).toByte()

            var sum = 0
            while (sum < nalSize) {
              val length = if (nalSize - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
                maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2
              } else {
                data.remaining()
              }
              val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2)
              buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
              // Switch start bit
              buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = if (sum > 0) header[1] and 0x7F else header[1]
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
              val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
              frames.add(rtpFrame)
            }
          }
      }
    } else {
      Log.i(TAG, "waiting for keyframe")
    }
    if (frames.isNotEmpty()) callback(frames)
  }

  private fun setSpsPps(sps: ByteArray, pps: ByteArray) {
    stapA = ByteArray(sps.size + pps.size + 5)
    stapA?.let {
      // STAP-A NAL header is 24
      it[0] = 24

      // Write NALU 1 size into the array (NALU 1 is the SPS).
      it[1] = (sps.size shr 8).toByte()
      it[2] = (sps.size and 0xFF).toByte()

      // Write NALU 2 size into the array (NALU 2 is the PPS).
      it[sps.size + 3] = (pps.size shr 8).toByte()
      it[sps.size + 4] = (pps.size and 0xFF).toByte()

      // Write NALU 1 into the array, then write NALU 2 into the array.
      System.arraycopy(sps, 0, it, 3, sps.size)
      System.arraycopy(pps, 0, it, 5 + sps.size, pps.size)
    }
  }

  override fun reset() {
    super.reset()
    sendKeyFrame = false
  }
}