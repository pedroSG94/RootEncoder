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

package com.pedro.udp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.UrlParser
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.SocketType
import com.pedro.common.toMediaFrameInfo
import com.pedro.common.validMessage
import com.pedro.udp.utils.UdpSocket
import com.pedro.common.socket.base.UdpType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URISyntaxException
import java.nio.ByteBuffer

/**
 * Created by pedro on 6/3/24.
 */
class UdpClient(private val connectChecker: ConnectChecker) {

  private val TAG = "UdpClient"

  private val validSchemes = arrayOf("udp")

  private val commandManager = CommandManager()
  private val udpSender = UdpSender(connectChecker, commandManager)
  private var socket: UdpSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  private var scopeRetry = CoroutineScope(Dispatchers.IO)
  private var jobRetry: Job? = null

  @Volatile
  var isStreaming = false
    private set
  private var url: String? = null
  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0

  val droppedAudioFrames: Long
    get() = udpSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = udpSender.droppedVideoFrames

  val cacheSize: Int
    get() = udpSender.getCacheSize()
  val sentAudioFrames: Long
    get() = udpSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = udpSender.getSentVideoFrames()
  var socketType = SocketType.KTOR

  fun setVideoCodec(videoCodec: VideoCodec) {
    if (!isStreaming) {
      commandManager.videoCodec = when (videoCodec) {
        VideoCodec.AV1 -> throw IllegalArgumentException("Unsupported codec: ${videoCodec.name}")
        else -> videoCodec
      }
    }
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    if (!isStreaming) {
      commandManager.audioCodec = when (audioCodec) {
        AudioCodec.G711 -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
        else -> audioCodec
      }
    }
  }

  fun setDelay(millis: Long) {
    udpSender.setDelay(millis)
  }

  /**
   * Must be called before connect
   */
  fun setOnlyAudio(onlyAudio: Boolean) {
    commandManager.audioDisabled = false
    commandManager.videoDisabled = onlyAudio
  }

  /**
   * Must be called before connect
   */
  fun setOnlyVideo(onlyVideo: Boolean) {
    commandManager.videoDisabled = false
    commandManager.audioDisabled = onlyVideo
  }

  fun setReTries(reTries: Int) {
    numRetry = reTries
    this.reTries = reTries
  }

  fun shouldRetry(reason: String): Boolean {
    val validReason = doingRetry && !reason.contains("Endpoint malformed")
    return validReason && reTries > 0
  }

  @JvmOverloads
  fun connect(url: String?, isRetry: Boolean = false) {
    if (!isRetry) doingRetry = true
    if (!isStreaming || isRetry) {
      isStreaming = true

      job = scope.launch {
        if (url == null) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: udp://ip:port")
          }
          return@launch
        }
        this@UdpClient.url = url
        onMainThread {
          connectChecker.onConnectionStarted(url)
        }

        val urlParser = try {
          UrlParser.parse(url, validSchemes)
        } catch (e: URISyntaxException) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: udp://ip:port")
          }
          return@launch
        }

        val host = urlParser.host
        val port = urlParser.port
        if (port == null) {
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, port is required")
          }
          return@launch
        }
        commandManager.host = host

        val error = runCatching {
          val type = UdpType.getTypeByHost(host)
          socket = UdpSocket(socketType, host, type, port)
          socket?.connect()

          udpSender.socket = socket
          udpSender.start()
          onMainThread {
            connectChecker.onConnectionSuccess()
          }
        }.exceptionOrNull()
        if (error != null) {
          Log.e(TAG, "connection error", error)
          onMainThread {
            connectChecker.onConnectionFailed("Error configure stream, ${error.validMessage()}")
          }
          return@launch
        }
      }
    }
  }

  fun disconnect() {
    CoroutineScope(Dispatchers.IO).launch {
      disconnect(true)
    }
  }

  private suspend fun disconnect(clear: Boolean) {
    if (isStreaming) udpSender.stop(clear)
    socket?.close()
    if (clear) {
      reTries = numRetry
      doingRetry = false
      isStreaming = false
      onMainThread {
        connectChecker.onDisconnect()
      }
      jobRetry?.cancelAndJoin()
      jobRetry = null
      scopeRetry.cancel()
      scopeRetry = CoroutineScope(Dispatchers.IO)
    }
    commandManager.reset()
    job?.cancelAndJoin()
    job = null
    scope.cancel()
    scope = CoroutineScope(Dispatchers.IO)
  }

  fun reConnect(delay: Long) {
    reConnect(delay, null)
  }

  fun reConnect(delay: Long, backupUrl: String?) {
    jobRetry = scopeRetry.launch {
      reTries--
      disconnect(false)
      delay(delay)
      val reconnectUrl = backupUrl ?: url
      connect(reconnectUrl, true)
    }
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    udpSender.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    udpSender.setVideoInfo(sps, pps, vps)
  }

  fun sendVideo(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandManager.videoDisabled) {
      udpSender.sendMediaFrame(MediaFrame(videoBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.VIDEO))
    }
  }

  fun sendAudio(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandManager.audioDisabled) {
      udpSender.sendMediaFrame(MediaFrame(audioBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.AUDIO))
    }
  }

  @Throws(IllegalArgumentException::class)
  fun hasCongestion(): Boolean {
    return hasCongestion(20f)
  }

  @Throws(IllegalArgumentException::class)
  fun hasCongestion(percentUsed: Float): Boolean {
    return udpSender.hasCongestion(percentUsed)
  }

  fun resetSentAudioFrames() {
    udpSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    udpSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    udpSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    udpSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    udpSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    udpSender.setLogs(enable)
  }

  fun clearCache() {
    udpSender.clearCache()
  }

  fun getItemsInCache(): Int = udpSender.getItemsInCache()

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  fun setBitrateExponentialFactor(factor: Float) {
    udpSender.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  fun getBitrateExponentialFactor() = udpSender.getBitrateExponentialFactor()
}