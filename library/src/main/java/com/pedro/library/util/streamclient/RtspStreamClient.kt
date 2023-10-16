package com.pedro.library.util.streamclient

import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspClient

/**
 * Created by pedro on 12/10/23.
 */
class RtspStreamClient(
  private val rtspClient: RtspClient,
  streamClientListener: StreamClientListener?
): StreamBaseClient(streamClientListener) {


  /**
   * Internet protocol used.
   *
   * @param protocol Could be Protocol.TCP or Protocol.UDP.
   */
  fun setProtocol(protocol: Protocol?) {
    rtspClient.setProtocol(protocol!!)
  }

  override fun setAuthorization(user: String?, password: String?) {
    rtspClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    rtspClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = rtspClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    rtspClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(percentUsed: Float): Boolean = rtspClient.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    rtspClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    rtspClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    rtspClient.resizeCache(newSize)
  }

  override fun clearCache() {
    rtspClient.clearCache()
  }

  override fun getCacheSize(): Int = rtspClient.cacheSize

  override fun getItemsInCache(): Int = rtspClient.getItemsInCache()

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

  override fun setOnlyAudio(onlyAudio: Boolean) {
    rtspClient.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    rtspClient.setOnlyVideo(onlyVideo)
  }
}