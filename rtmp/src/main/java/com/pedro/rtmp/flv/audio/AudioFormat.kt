package com.pedro.rtmp.flv.audio

/**
 * Created by pedro on 29/04/21.
 * list of audio codec supported by FLV
 */
enum class AudioFormat(val value: Int) {
  PCM(0), ADPCM(1), MP3(2), PCM_LE(3), NELLYMOSER_16K(4),
  NELLYMOSER_8K(5), NELLYMOSER(6), G711_A(7), G711_MU(8), RESERVED(9),
  AAC(10), SPEEX(11), MP3_8K(14), DEVICE_SPECIFIC(15)
}