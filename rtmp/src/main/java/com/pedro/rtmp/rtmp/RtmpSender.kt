package com.pedro.rtmp.rtmp

import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.audio.AacPacket
import com.pedro.rtmp.flv.audio.AudioPacketCallback
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/04/21.
 */
class RtmpSender : AudioPacketCallback {

  private val aacPacket = AacPacket(this)

  fun setVideoInfo(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {

  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun start() {

  }

  fun stop() {
    aacPacket.reset()
  }

  override fun onAudioFrameCreated(flvPacket: FlvPacket) {

  }
}