package com.pedro.rtmp.flv.audio

/**
 * Created by pedro on 29/04/21.
 * sample rates supported
 */
enum class AudioSoundRate(mark: Byte) {
  SR_5_5K(0x00), SR_11K(0x01), SR_22K(0x02), SR_44_1K(0x03)
}