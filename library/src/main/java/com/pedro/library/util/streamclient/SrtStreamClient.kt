package com.pedro.library.util.streamclient

import com.pedro.srt.srt.SrtClient

/**
 * Created by pedro on 12/10/23.
 */
class SrtStreamClient(
  private val srtClient: SrtClient,
  streamClientListener: StreamClientListener?
): StreamBaseClient(streamClientListener) {

  override fun setAuthorization(user: String?, password: String?) {
    srtClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    srtClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = srtClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    srtClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(percentUsed: Float): Boolean = srtClient.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    srtClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    srtClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    srtClient.resizeCache(newSize)
  }

  override fun clearCache() {
    srtClient.clearCache()
  }

  override fun getCacheSize(): Int = srtClient.cacheSize

  override fun getSentAudioFrames(): Long = srtClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = srtClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = srtClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = srtClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    srtClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    srtClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    srtClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    srtClient.resetDroppedVideoFrames()
  }
}