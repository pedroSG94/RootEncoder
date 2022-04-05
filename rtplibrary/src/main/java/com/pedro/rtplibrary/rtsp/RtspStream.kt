package com.pedro.rtplibrary.rtsp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.rtplibrary.base.StreamBase
import com.pedro.rtplibrary.util.sources.AudioManager
import com.pedro.rtplibrary.util.sources.VideoManager
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

  constructor(context: Context, connectCheckerRtsp: ConnectCheckerRtsp):
      this(context, connectCheckerRtsp, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  private val rtspClient = RtspClient(connectCheckerRtsp)

  /**
   * Internet protocol used.
   *
   * @param protocol Could be Protocol.TCP or Protocol.UDP.
   */
  fun setProtocol(protocol: Protocol?) {
    rtspClient.setProtocol(protocol!!)
  }

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

  override fun setAuthorization(user: String?, password: String?) {
    rtspClient.setAuthorization(user, password)
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

  override fun setReTries(reTries: Int) {
    rtspClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = rtspClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    rtspClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(): Boolean = rtspClient.hasCongestion()

  override fun setLogs(enabled: Boolean) {
    rtspClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    rtspClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    rtspClient.resizeCache(newSize)
  }

  override fun getCacheSize(): Int = rtspClient.cacheSize

  override fun getSentAudioFrames(): Long = rtspClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = rtspClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = rtspClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = rtspClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    rtspClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    rtspClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    rtspClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    rtspClient.resetDroppedVideoFrames()
  }
}