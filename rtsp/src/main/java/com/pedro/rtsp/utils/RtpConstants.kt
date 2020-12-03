package com.pedro.rtsp.utils

/**
 * Created by pedro on 19/02/17.
 */
internal object RtpConstants {

  @JvmField
  val lock = Any()
  const val clockVideoFrequency = 90000L
  const val RTP_HEADER_LENGTH = 12
  const val MTU = 1300
  const val payloadTypeVideo = 96
  const val payloadTypeAudio = 97

  //H264 IDR
  const val IDR = 5

  //H265 IDR
  const val IDR_N_LP = 20
  const val IDR_W_DLP = 19
}