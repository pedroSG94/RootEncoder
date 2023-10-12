package com.pedro.library.rtsp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.library.base.StreamBase
import com.pedro.library.util.client.RtspStreamClient
import com.pedro.library.util.client.SrtStreamClient
import com.pedro.library.util.sources.AudioManager
import com.pedro.library.util.sources.VideoManager
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.rtsp.rtsp.VideoCodec
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtspStream(context: Context, connectCheckerRtsp: ConnectCheckerRtsp, videoSource: VideoManager.Source,
  audioSource: AudioManager.Source): StreamBase(context, videoSource, audioSource) {

  private val rtspClient = RtspClient(connectCheckerRtsp)
  val streamClient = RtspStreamClient(rtspClient)

  constructor(context: Context, connectCheckerRtsp: ConnectCheckerRtsp):
      this(context, connectCheckerRtsp, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  fun setVideoCodec(videoCodec: VideoCodec) {
    val mime = if (videoCodec === VideoCodec.H265) CodecUtil.H265_MIME else CodecUtil.H264_MIME
    super.setVideoMime(mime)
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

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  @JvmOverloads
  fun reTry(delay: Long, reason: String, backupUrl: String? = null): Boolean {
    val result = streamClient.shouldRetry(reason)
    if (result) {
      requestKeyframe()
      streamClient.reConnect(delay, backupUrl)
    }
    return result
  }
}