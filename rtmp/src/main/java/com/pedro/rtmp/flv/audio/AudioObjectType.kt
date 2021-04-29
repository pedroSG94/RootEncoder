package com.pedro.rtmp.flv.audio

/**
 * Created by pedro on 29/04/21.
 */
enum class AudioObjectType(val value: Int) {
  UNKNOWN(0), AAC_MAIN(1), AAC_LC(2), AAC_SSR(3), AAC_LTP(4),
  AAC_SBR(5), AAC_SCALABLE(6), TWINQ_VQ(7), CELP(8), HXVC(9)
}