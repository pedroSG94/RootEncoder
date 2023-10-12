package com.pedro.library.util.client

/**
 * Created by pedro on 12/10/23.
 */
abstract class StreamBaseClient {

  /**
   *
   * @param user auth.
   * @param password auth.
   */
  abstract fun setAuthorization(user: String?, password: String?)
  abstract fun shouldRetry(reason: String): Boolean
  abstract fun reConnect(delay: Long, backupUrl: String?)
  abstract fun setReTries(reTries: Int)
  abstract fun hasCongestion(): Boolean
  abstract fun setLogs(enabled: Boolean)
  abstract fun setCheckServerAlive(enabled: Boolean)
  @Throws(RuntimeException::class)
  abstract fun resizeCache(newSize: Int)
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