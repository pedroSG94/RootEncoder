package com.pedro.rtmp.flv.video

/**
 * Created by pedro on 29/04/21.
 */
enum class VideoNalType(val value: Int) {
  UNSPEC(0), SLICE(1), DPA(2), DPB(3), DPC(4), IDR(5), SEI(6),
  SPS(7), PPS(8), AUD(9), EO_SEQ(10), EO_STREAM(11), FILL(12)
}