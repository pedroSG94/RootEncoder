package com.pedro.library.util.streamclient

import com.pedro.rtmp.flv.video.ProfileIop
import com.pedro.rtmp.rtmp.RtmpClient

/**
 * Created by pedro on 12/10/23.
 */
class RtmpStreamClient(
  private val rtmpClient: RtmpClient, 
  streamClientListener: StreamClientListener?
): StreamBaseClient(streamClientListener) {

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
   * Must be called before start stream or will be ignored.
   *
   * Default value 128
   * Range value: 1 to 16777215.
   *
   * The most common values example: 128, 4096, 65535
   *
   * @param chunkSize packet's chunk size send to server
   */
  fun setWriteChunkSize(chunkSize: Int) {
    rtmpClient.setWriteChunkSize(chunkSize)
  }

  override fun setAuthorization(user: String?, password: String?) {
    rtmpClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    rtmpClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = rtmpClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    rtmpClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(percentUsed: Float): Boolean = rtmpClient.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    rtmpClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    rtmpClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    rtmpClient.resizeCache(newSize)
  }

  override fun clearCache() {
    rtmpClient.clearCache()
  }

  override fun getCacheSize(): Int = rtmpClient.cacheSize

  override fun getItemsInCache(): Int = rtmpClient.getItemsInCache()

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

  override fun setOnlyAudio(onlyAudio: Boolean) {
    rtmpClient.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    rtmpClient.setOnlyVideo(onlyVideo)
  }
}