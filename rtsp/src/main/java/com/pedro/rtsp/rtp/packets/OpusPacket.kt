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

import android.media.MediaCodec
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/2/24.
 *
 * RFC 7587.
 */
class OpusPacket(
  sampleRate: Int
): BasePacket(
  sampleRate.toLong(),
  RtpConstants.payloadType + RtpConstants.trackAudio
) {

  init {
    channelIdentifier = RtpConstants.trackAudio
  }

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    bufferInfo: MediaCodec.BufferInfo,
    callback: (List<RtpFrame>) -> Unit
  ) {
    val length = bufferInfo.size - byteBuffer.position()
    val maxPayload = maxPacketSize - RtpConstants.RTP_HEADER_LENGTH
    val ts = bufferInfo.presentationTimeUs * 1000
    var sum = 0
    val frames = mutableListOf<RtpFrame>()
    while (sum < length) {
      val size = if (length - sum < maxPayload) length - sum else maxPayload
      val buffer = getBuffer(size + RtpConstants.RTP_HEADER_LENGTH)
      byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH, size)
      markPacket(buffer)
      val rtpTs = updateTimeStamp(buffer, ts)
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, RtpConstants.RTP_HEADER_LENGTH + size , rtpPort, rtcpPort, channelIdentifier)
      sum += size
      frames.add(rtpFrame)
    }
    callback(frames)
  }
}