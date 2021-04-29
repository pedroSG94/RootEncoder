package com.pedro.rtmp.flv.video

import com.pedro.rtmp.flv.FlvPacket

/**
 * Created by pedro on 29/04/21.
 */
interface VideoPacketCallback {
  fun onVideoFrameCreated(flvPacket: FlvPacket)
}