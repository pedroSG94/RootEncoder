package com.pedro.rtplibrary.rtmp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.rtmp.flv.video.ProfileIop
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.base.StreamBase
import com.pedro.rtplibrary.util.sources.AudioManager
import com.pedro.rtplibrary.util.sources.VideoManager
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtmpStream(context: Context, connectCheckerRtmp: ConnectCheckerRtmp, videoSource: VideoManager.Source,
  audioSource: AudioManager.Source): StreamBase(context, videoSource, audioSource) {

  constructor(context: Context, connectCheckerRtmp: ConnectCheckerRtmp):
      this(context, connectCheckerRtmp, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  private val rtmpClient = RtmpClient(connectCheckerRtmp)

  /**
   * H264 profile.
   *
   * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
   */
  fun setProfileIop(profileIop: ProfileIop?) {
    rtmpClient.setProfileIop(profileIop!!)
  }

  /**
   * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
   * timestamp order regardless of packet type.
   * Necessary with Servers like Dacast.
   * More info here:
   * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
   */
  fun forceAkamaiTs(enabled: Boolean) {
    rtmpClient.forceAkamaiTs(enabled)
  }

  /**
   * Must be called before start stream.
   *
   * Default value 128
   * Range value: 1 to 16777215.
   *
   * The most common values example: 128, 4096, 65535
   *
   * @param chunkSize packet's chunk size send to server
   */
  fun setWriteChunkSize(chunkSize: Int) {
    if (!isStreaming) {
      rtmpClient.setWriteChunkSize(chunkSize)
    }
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

  override fun setAuthorization(user: String?, password: String?) {
    rtmpClient.setAuthorization(user, password)
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

  override fun setReTries(reTries: Int) {
    rtmpClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = rtmpClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    rtmpClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(): Boolean = rtmpClient.hasCongestion()

  override fun setLogs(enabled: Boolean) {
    rtmpClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    rtmpClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    rtmpClient.resizeCache(newSize)
  }

  override fun getCacheSize(): Int = rtmpClient.cacheSize

  override fun getSentAudioFrames(): Long = rtmpClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = rtmpClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = rtmpClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = rtmpClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    rtmpClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    rtmpClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    rtmpClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    rtmpClient.resetDroppedVideoFrames()
  }
}