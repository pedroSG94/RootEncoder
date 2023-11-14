package com.pedro.srt.srt.packets.control.handshake.extension

/**
 * Created by pedro on 13/11/23.
 */
enum class StreamEncapsulationType(val value: Int) {
  Unspecified(0), MPEG_TS_UDP(1), MPEG_TS_SRT(2)
}