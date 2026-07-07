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

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 8/2/24.
 *
 * RFC 7587.
 */
class OpusPacket(track: Int): BasePacket(0, RtpConstants.payloadType + track) {

  init {
    channelIdentifier = track
  }

  fun setAudioInfo(sampleRate: Int) {
    setClock(sampleRate.toLong())
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val length = mediaFrame.info.size - mediaFrame.data.position()
    val maxPayload = maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - encryptSize()
    val ts = mediaFrame.info.timestamp * 1000
    var sum = 0
    val frames = mutableListOf<RtpFrame>()
    while (sum < length) {
      val size = if (length - sum < maxPayload) length - sum else maxPayload
      val buffer = getBuffer(size + RtpConstants.RTP_HEADER_LENGTH + encryptSize())
      mediaFrame.data.get(buffer, RtpConstants.RTP_HEADER_LENGTH, size)
      val rtpTs = updateTimeStamp(buffer, ts)
      sum += size
      if (sum >= length) markPacket(buffer)
      updateSeq(buffer)
      encryptPacket(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
      frames.add(rtpFrame)
    }
    if (frames.isNotEmpty()) callback(frames)
  }
}