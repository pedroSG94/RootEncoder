package com.pedro.rtsp.rtsp

/**
 * Created by pedro on 7/11/18.
 */
data class RtpFrame(val buffer: ByteArray, val timeStamp: Long, val length: Int,
                    val rtpPort: Int, val rtcpPort: Int, val channelIdentifier: Byte) {

  fun isVideoFrame(): Boolean = channelIdentifier == 2.toByte()
}