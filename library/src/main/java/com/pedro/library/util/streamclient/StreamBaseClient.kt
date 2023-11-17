/*
 * Copyright (C) 2023 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.util.streamclient

/**
 * Created by pedro on 12/10/23.
 *
 * Provide access to rtmp/rtsp/srt client methods that is expected to be used.
 * This way we can hide method that should be handled only by the library.
 */
abstract class StreamBaseClient {

  fun reTry(delay: Long, reason: String): Boolean = reTry(delay, reason, null)

  /**
   *
   * @param user auth.
   * @param password auth.
   */
  abstract fun setAuthorization(user: String?, password: String?)

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  abstract fun reTry(delay: Long, reason: String, backupUrl: String? = null): Boolean
  abstract fun setReTries(reTries: Int)
  fun hasCongestion(): Boolean = hasCongestion(20f)
  abstract fun hasCongestion(percentUsed: Float): Boolean
  abstract fun setLogs(enabled: Boolean)
  abstract fun setCheckServerAlive(enabled: Boolean)
  @Throws(RuntimeException::class)
  abstract fun resizeCache(newSize: Int)
  abstract fun clearCache()
  abstract fun getCacheSize(): Int
  abstract fun getItemsInCache(): Int
  abstract fun getSentAudioFrames(): Long
  abstract fun getSentVideoFrames(): Long
  abstract fun getDroppedAudioFrames(): Long
  abstract fun getDroppedVideoFrames(): Long
  abstract fun resetSentAudioFrames()
  abstract fun resetSentVideoFrames()
  abstract fun resetDroppedAudioFrames()
  abstract fun resetDroppedVideoFrames()
  abstract fun setOnlyAudio(onlyAudio: Boolean)
  abstract fun setOnlyVideo(onlyVideo: Boolean)
}