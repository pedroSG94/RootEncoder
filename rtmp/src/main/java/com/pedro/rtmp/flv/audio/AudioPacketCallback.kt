package com.pedro.rtmp.flv.audio

import com.pedro.rtmp.flv.FlvPacket

/**
 * Created by pedro on 29/04/21.
 */
interface AudioPacketCallback {
  fun onAudioFrameCreated(flvPacket: FlvPacket)
}