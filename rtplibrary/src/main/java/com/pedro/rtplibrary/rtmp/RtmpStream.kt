package com.pedro.rtplibrary.rtmp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.base.StreamBase
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtmpStream(context: Context, connectCheckerRtmp: ConnectCheckerRtmp): StreamBase(context = context) {

  private val rtmpClient = RtmpClient(connectCheckerRtmp)

  override fun videoInfo(width: Int, height: Int) {
    rtmpClient.setVideoResolution(width, height)
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) {
    rtmpClient.connect(endPoint)
  }

  override fun rtpStopStream() {
    rtmpClient.disconnect()
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    rtmpClient.setVideoInfo(sps, pps, vps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtmpClient.sendVideo(h264Buffer, info)
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtmpClient.sendAudio(aacBuffer, info)
  }
}