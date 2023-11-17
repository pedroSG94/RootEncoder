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

import com.pedro.srt.srt.SrtClient
import com.pedro.srt.srt.packets.control.handshake.EncryptionType

/**
 * Created by pedro on 12/10/23.
 */
class SrtStreamClient(
  private val srtClient: SrtClient,
  private val streamClientListener: StreamClientListener?
): StreamBaseClient() {

  /**
   * Set passphrase for encrypt. Use empty value to disable it.
   */
  fun setPassphrase(passphrase: String, type: EncryptionType) {
    srtClient.setPassphrase(passphrase, type)
  }

  override fun setAuthorization(user: String?, password: String?) {
    srtClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    srtClient.setReTries(reTries)
  }

  override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean {
    val result = srtClient.shouldRetry(reason)
    if (result) {
      streamClientListener?.onRequestKeyframe()
      srtClient.reConnect(delay, backupUrl)
    }
    return result
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

  override fun getItemsInCache(): Int = srtClient.getItemsInCache()

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

  override fun setOnlyAudio(onlyAudio: Boolean) {
    srtClient.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    srtClient.setOnlyVideo(onlyVideo)
  }
}