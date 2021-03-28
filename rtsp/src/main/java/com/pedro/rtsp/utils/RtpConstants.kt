package com.pedro.rtsp.utils

/**
 * Created by pedro on 19/02/17.
 */
object RtpConstants {

  @JvmField
  val lock = Any()
  var trackAudio = 1
  var trackVideo = 0
  const val clockVideoFrequency = 90000L
  const val RTP_HEADER_LENGTH = 12
  const val MTU = 1500
  const val payloadType = 96

  //H264 IDR
  const val IDR = 5

  //H265 IDR
  const val IDR_N_LP = 20
  const val IDR_W_DLP = 19
}