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

import com.pedro.udp.UdpClient

/**
 * Created by pedro on 6/3/24.
 */
class UdpStreamClient(
  private val udpClient: UdpClient,
  private val streamClientListener: StreamClientListener?
): StreamBaseClient() {


  override fun setAuthorization(user: String?, password: String?) {

  }

  override fun setReTries(reTries: Int) {
    udpClient.setReTries(reTries)
  }

  override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean {
    val result = udpClient.shouldRetry(reason)
    if (result) {
      streamClientListener?.onRequestKeyframe()
      udpClient.reConnect(delay, backupUrl)
    }
    return result
  }

  override fun hasCongestion(percentUsed: Float): Boolean = udpClient.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    udpClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {

  }

  override fun resizeCache(newSize: Int) {
    udpClient.resizeCache(newSize)
  }

  override fun clearCache() {
    udpClient.clearCache()
  }

  override fun getCacheSize(): Int = udpClient.cacheSize

  override fun getItemsInCache(): Int = udpClient.getItemsInCache()

  override fun getSentAudioFrames(): Long = udpClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = udpClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = udpClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = udpClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    udpClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    udpClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    udpClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    udpClient.resetDroppedVideoFrames()
  }

  override fun setOnlyAudio(onlyAudio: Boolean) {
    udpClient.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    udpClient.setOnlyVideo(onlyVideo)
  }

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  override fun setBitrateExponentialFactor(factor: Float) {
    udpClient.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  override fun getBitrateExponentialFactor() = udpClient.getBitrateExponentialFactor()
}