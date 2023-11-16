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

package com.pedro.rtsp.rtsp

import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 7/11/18.
 */
data class RtpFrame(val buffer: ByteArray, val timeStamp: Long, val length: Int,
                    val rtpPort: Int, val rtcpPort: Int, val channelIdentifier: Int) {

  fun isVideoFrame(): Boolean = channelIdentifier == RtpConstants.trackVideo

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RtpFrame

    if (!buffer.contentEquals(other.buffer)) return false
    if (timeStamp != other.timeStamp) return false
    if (length != other.length) return false
    if (rtpPort != other.rtpPort) return false
    if (rtcpPort != other.rtcpPort) return false
    if (channelIdentifier != other.channelIdentifier) return false

    return true
  }

  override fun hashCode(): Int {
    var result = buffer.contentHashCode()
    result = 31 * result + timeStamp.hashCode()
    result = 31 * result + length
    result = 31 * result + rtpPort
    result = 31 * result + rtcpPort
    result = 31 * result + channelIdentifier
    return result
  }
}