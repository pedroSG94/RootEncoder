/*
 * Copyright (C) 2024 pedroSG94.
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

import com.pedro.whip.WhipClient

/**
 * Created by pedro on 12/10/23.
 */
class WhipStreamClient(
  private val whipClient: WhipClient,
  private val streamClientListener: StreamClientListener?
): StreamBaseClient() {


  override fun setAuthorization(user: String?, password: String?) {
    whipClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    whipClient.setReTries(reTries)
  }

  override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean {
    val result = whipClient.shouldRetry(reason)
    if (result) {
      streamClientListener?.onRequestKeyframe()
      whipClient.reConnect(delay, backupUrl)
    }
    return result
  }

  override fun hasCongestion(percentUsed: Float): Boolean = whipClient.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    whipClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    whipClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    whipClient.resizeCache(newSize)
  }

  override fun clearCache() {
    whipClient.clearCache()
  }

  override fun getCacheSize(): Int = whipClient.cacheSize

  override fun getItemsInCache(): Int = whipClient.getItemsInCache()

  override fun getSentAudioFrames(): Long = whipClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = whipClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = whipClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = whipClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    whipClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    whipClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    whipClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    whipClient.resetDroppedVideoFrames()
  }

  override fun setOnlyAudio(onlyAudio: Boolean) {
    whipClient.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    whipClient.setOnlyVideo(onlyVideo)
  }

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  override fun setBitrateExponentialFactor(factor: Float) {
    whipClient.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  override fun getBitrateExponentialFactor() = whipClient.getBitrateExponentialFactor()
}