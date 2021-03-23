package com.pedro.rtsp.rtsp

import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 7/11/18.
 */
data class RtpFrame(val buffer: ByteArray, val timeStamp: Long, val length: Int,
                    val rtpPort: Int, val rtcpPort: Int, val channelIdentifier: Int) {

  fun isVideoFrame(): Boolean = channelIdentifier == RtpConstants.trackVideo
}