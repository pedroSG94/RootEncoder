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

import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspClient
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 12/10/23.
 */
class RtspStreamClient(
  private val rtspClient: RtspClient,
  private val streamClientListener: StreamClientListener?
): StreamBaseClient() {

  /**
   * Add certificates for TLS connection
   */
  fun addCertificates(certificates: Array<TrustManager>?) {
    rtspClient.addCertificates(certificates)
  }

  /**
   * Internet protocol used.
   *
   * @param protocol Could be Protocol.TCP or Protocol.UDP.
   */
  fun setProtocol(protocol: Protocol) {
    rtspClient.setProtocol(protocol)
  }

  override fun setAuthorization(user: String?, password: String?) {
    rtspClient.setAuthorization(user, password)
  }

  override fun setReTries(reTries: Int) {
    rtspClient.setReTries(reTries)
  }

  override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean {
    val result = rtspClient.shouldRetry(reason)
    if (result) {
      streamClientListener?.onRequestKeyframe()
      rtspClient.reConnect(delay, backupUrl)
    }
    return result
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