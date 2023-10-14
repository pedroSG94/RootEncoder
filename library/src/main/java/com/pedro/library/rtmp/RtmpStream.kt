package com.pedro.library.rtmp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.library.base.StreamBase
import com.pedro.library.util.sources.AudioManager
import com.pedro.library.util.sources.VideoManager
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtmp.rtmp.VideoCodec
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtmpStream(
  context: Context, connectCheckerRtmp: ConnectCheckerRtmp, videoSource: VideoManager.Source,
  audioSource: AudioManager.Source
): StreamBase(context, videoSource, audioSource) {

  private val rtmpClient = RtmpClient(connectCheckerRtmp)
  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyframe()
    }
  }
  val streamClient = RtmpStreamClient(rtmpClient, streamClientListener)

  constructor(context: Context, connectCheckerRtmp: ConnectCheckerRtmp):
      this(context, connectCheckerRtmp, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  fun setVideoCodec(videoCodec: VideoCodec) {
    val mime = if (videoCodec === VideoCodec.H265) CodecUtil.H265_MIME else CodecUtil.H264_MIME
    super.setVideoMime(mime)
    rtmpClient.setVideoCodec(videoCodec)
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) {
    val resolution = super.getVideoResolution()
    rtmpClient.setVideoResolution(resolution.width, resolution.height)
    rtmpClient.setFps(super.getVideoFps())
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