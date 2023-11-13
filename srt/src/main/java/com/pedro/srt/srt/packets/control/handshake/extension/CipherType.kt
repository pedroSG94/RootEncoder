package com.pedro.srt.srt.packets.control.handshake.extension

/**
 * Created by pedro on 13/11/23.
 */
enum class CipherType(val value: Int) {
  NONE(0), ECB(1), CTR(2), CBC(3), GCM(4)
}