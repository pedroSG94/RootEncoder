package com.pedro.library.util.streamclient

/**
 * Created by pedro on 12/10/23.
 *
 * Provide access to rtmp/rtsp/srt client methods that is expected to be used.
 * This way we can hide method that should be handled only by the library.
 */
abstract class StreamBaseClient(
  private val streamClientListener: StreamClientListener?
) {

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  @JvmOverloads
  fun reTry(delay: Long, reason: String, backupUrl: String? = null): Boolean {
    val result = shouldRetry(reason)
    if (result) {
      streamClientListener?.onRequestKeyframe()
      reConnect(delay, backupUrl)
    }
    return result
  }

  /**
   *
   * @param user auth.
   * @param password auth.
   */
  abstract fun setAuthorization(user: String?, password: String?)
  protected abstract fun shouldRetry(reason: String): Boolean
  protected abstract fun reConnect(delay: Long, backupUrl: String?)
  abstract fun setReTries(reTries: Int)
  fun hasCongestion(): Boolean = hasCongestion(20f)
  abstract fun hasCongestion(percentUsed: Float): Boolean
  abstract fun setLogs(enabled: Boolean)
  abstract fun setCheckServerAlive(enabled: Boolean)
  @Throws(RuntimeException::class)
  abstract fun resizeCache(newSize: Int)
  abstract fun clearCache()
  abstract fun getCacheSize(): Int
  abstract fun getSentAudioFrames(): Long
  abstract fun getSentVideoFrames(): Long
  abstract fun getDroppedAudioFrames(): Long
  abstract fun getDroppedVideoFrames(): Long
  abstract fun resetSentAudioFrames()
  abstract fun resetSentVideoFrames()
  abstract fun resetDroppedAudioFrames()
  abstract fun resetDroppedVideoFrames()
}