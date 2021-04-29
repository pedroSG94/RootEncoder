package com.pedro.rtmp.flv.video

/**
 * Created by pedro on 29/04/21.
 */
enum class VideoFormat(val value: Int) {
  SORENSON_H263(2), SCREEN_1(3), VP6(4), VP6_ALPHA(5),
  SCREEN_2(6), AVC(7), UNKNOWN(255)
}