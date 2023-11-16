package com.pedro.library.rtsp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.StreamBase
import com.pedro.library.util.sources.AudioManager
import com.pedro.library.util.sources.VideoManager
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtsp.rtsp.RtspClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtspStream(
  context: Context, connectChecker: ConnectChecker, videoSource: VideoManager.Source,
  audioSource: AudioManager.Source
): StreamBase(context, videoSource, audioSource) {

  private val rtspClient = RtspClient(connectChecker)
  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyframe()
    }
  }
  override fun getStreamClient(): RtspStreamClient = RtspStreamClient(rtspClient, streamClientListener)

  constructor(context: Context, connectChecker: ConnectChecker):
      this(context, connectChecker, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  override fun setVideoCodecImp(codec: VideoCodec) {
      rtspClient.setVideoCodec(codec)
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    rtspClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) {
    rtspClient.connect(endPoint)
  }

  override fun rtpStopStream() {
    rtspClient.disconnect()
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    rtspClient.setVideoInfo(sps, pps, vps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspClient.sendVideo(h264Buffer, info)
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspClient.sendAudio(aacBuffer, info)
  }
}