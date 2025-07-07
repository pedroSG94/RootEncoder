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

package com.pedro.rtsp.rtsp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.ConnectionFailed
import com.pedro.common.UrlParser
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.common.toMediaFrameInfo
import com.pedro.common.validMessage
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.*
import java.net.URISyntaxException
import java.nio.ByteBuffer
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 10/02/17.
 */
class RtspClient(private val connectChecker: ConnectChecker) {

  private val TAG = "RtspClient"

  private val validSchemes = arrayOf("rtsp", "rtsps")

  //sockets objects
  private var socket: TcpStreamSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var scopeRetry = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  private var jobRetry: Job? = null
  private var mutex = Mutex(locked = true)

  @Volatile
  var isStreaming = false
    private set

  //for secure transport
  private var tlsEnabled = false
  private var certificates: TrustManager? = null
  private val commandsManager: CommandsManager = CommandsManager()
  private val rtspSender: RtspSender = RtspSender(connectChecker, commandsManager)
  private var url: String? = null
  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0
  private var checkServerAlive = false

  val droppedAudioFrames: Long
    get() = rtspSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = rtspSender.droppedVideoFrames

  val cacheSize: Int
    get() = rtspSender.getCacheSize()
  val sentAudioFrames: Long
    get() = rtspSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = rtspSender.getSentVideoFrames()
  var socketType = SocketType.KTOR

  /**
   * Add certificates for TLS connection
   */
  fun addCertificates(certificates: TrustManager?) {
    this.certificates = certificates
  }

  /**
   * Check periodically if server is alive using Echo protocol.
   */
  fun setCheckServerAlive(enabled: Boolean) {
    checkServerAlive = enabled
  }

  /**
   * Must be called before connect
   */
  fun setOnlyAudio(onlyAudio: Boolean) {
    if (onlyAudio) {
      RtpConstants.trackAudio = 0
      RtpConstants.trackVideo = 1
    } else {
      RtpConstants.trackVideo = 0
      RtpConstants.trackAudio = 1
    }
    commandsManager.audioDisabled = false
    commandsManager.videoDisabled = onlyAudio
  }

  /**
   * Must be called before connect
   */
  fun setOnlyVideo(onlyVideo: Boolean) {
    RtpConstants.trackVideo = 0
    RtpConstants.trackAudio = 1
    commandsManager.videoDisabled = false
    commandsManager.audioDisabled = onlyVideo
  }

  fun setProtocol(protocol: Protocol) {
    commandsManager.protocol = protocol
  }

  fun setAuthorization(user: String?, password: String?) {
    commandsManager.setAuth(user, password)
  }

  fun setReTries(reTries: Int) {
    numRetry = reTries
    this.reTries = reTries
  }

  fun shouldRetry(reason: String): Boolean {
    val validReason = doingRetry && !reason.contains("Endpoint malformed")
    return validReason && reTries > 0
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    commandsManager.setVideoInfo(sps, pps, vps)
    if (mutex.isLocked) runCatching { mutex.unlock() }
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    commandsManager.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoCodec(videoCodec: VideoCodec) {
    if (!isStreaming) {
      commandsManager.videoCodec = videoCodec
    }
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    if (!isStreaming) {
      commandsManager.audioCodec = audioCodec
    }
  }

  fun setDelay(millis: Long) {
    rtspSender.setDelay(millis)
  }

  fun connect(url: String?) {
    connect(url, false)
  }

  fun connect(url: String?, isRetry: Boolean) {
    if (!isRetry) doingRetry = true
    if (!isStreaming || isRetry) {
      isStreaming = true

      job = scope.launch {
        if (url == null) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
          }
          return@launch
        }
        this@RtspClient.url = url
        onMainThread {
          connectChecker.onConnectionStarted(url)
        }

        val urlParser = try {
          UrlParser.parse(url, validSchemes)
        } catch (e: URISyntaxException) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
          }
          return@launch
        }

        tlsEnabled = urlParser.scheme.endsWith("s")
        val host = urlParser.host
        val port = urlParser.port ?: if (tlsEnabled) 443 else 554
        val path = urlParser.getFullPath()
        if (path.isEmpty()) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
          }
          return@launch
        }
        val user = urlParser.getAuthUser()
        val password = urlParser.getAuthPassword()
        if (user != null && password != null) setAuthorization(user, password)

        val error = runCatching {
          commandsManager.setUrl(host, port, "/$path")
          if (!commandsManager.audioDisabled) {
            rtspSender.setAudioInfo(commandsManager.sampleRate, commandsManager.isStereo)
          }
          if (!commandsManager.videoDisabled) {
            if (!commandsManager.videoInfoReady()) {
              Log.i(TAG, "waiting for sps and pps")
              withTimeoutOrNull(5000) {
                mutex.lock()
              }
              if (!commandsManager.videoInfoReady()) {
                onMainThread {
                  connectChecker.onConnectionFailed("sps or pps is null")
                }
                return@launch
              }
            }
            rtspSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps, commandsManager.vps)
          }
          val socket = StreamSocket.createTcpSocket(socketType, host, port, tlsEnabled, certificates)
          this@RtspClient.socket = socket
          socket.connect()
          socket.write(commandsManager.createOptions())
          socket.flush()
          commandsManager.getResponse(socket, Method.OPTIONS)
          socket.write(commandsManager.createAnnounce())
          socket.flush()
          //check if you need credential for stream, if you need try connect with credential
          val announceResponse = commandsManager.getResponse(socket, Method.ANNOUNCE)
          when (announceResponse.status) {
            403 -> {
              onMainThread {
                connectChecker.onConnectionFailed("Error configure stream, access denied")
              }
              Log.e(TAG, "Response 403, access denied")
              return@launch
            }
            401 -> {
              if (commandsManager.user == null || commandsManager.password == null) {
                onMainThread {
                  connectChecker.onAuthError()
                }
                return@launch
              } else {
                socket.write(commandsManager.createAnnounceWithAuth(announceResponse.text))
                socket.flush()
                when (commandsManager.getResponse(socket, Method.ANNOUNCE).status) {
                  401 -> {
                    onMainThread {
                      connectChecker.onAuthError()
                    }
                    return@launch
                  }
                  200 -> {
                    onMainThread {
                      connectChecker.onAuthSuccess()
                    }
                  }
                  else -> {
                    onMainThread {
                      connectChecker.onConnectionFailed("Error configure stream, announce with auth failed")
                    }
                    return@launch
                  }
                }
              }
            }
            200 -> {
              Log.i(TAG, "announce success")
            }
            else -> {
              onMainThread {
                connectChecker.onConnectionFailed("Error configure stream, announce failed")
              }
              return@launch
            }
          }
          if (!commandsManager.videoDisabled) {
            socket.write(commandsManager.createSetup(RtpConstants.trackVideo))
            socket.flush()
            val setupVideoStatus = commandsManager.getResponse(socket, Method.SETUP).status
            if (setupVideoStatus != 200) {
              onMainThread {
                connectChecker.onConnectionFailed("Error configure stream, setup video $setupVideoStatus")
              }
              return@launch
            }
          }
          if (!commandsManager.audioDisabled) {
            socket.write(commandsManager.createSetup(RtpConstants.trackAudio))
            socket.flush()
            val setupAudioStatus = commandsManager.getResponse(socket, Method.SETUP).status
            if (setupAudioStatus != 200) {
              onMainThread {
                connectChecker.onConnectionFailed("Error configure stream, setup audio $setupAudioStatus")
              }
              return@launch
            }
          }
          socket.write(commandsManager.createRecord())
          socket.flush()
          val recordStatus = commandsManager.getResponse(socket, Method.RECORD).status
          if (recordStatus != 200) {
            onMainThread {
              connectChecker.onConnectionFailed("Error configure stream, record $recordStatus")
            }
            return@launch
          }
          val videoClientPorts = if (!commandsManager.videoDisabled) {
            commandsManager.videoClientPorts
          } else arrayOf<Int?>(null, null)
          val videoServerPorts = if (!commandsManager.videoDisabled) {
            commandsManager.videoServerPorts
          } else arrayOf<Int?>(null, null)
          val audioClientPorts = if (!commandsManager.audioDisabled) {
            commandsManager.audioClientPorts
          } else arrayOf<Int?>(null, null)
          val audioServerPorts = if (!commandsManager.audioDisabled) {
            commandsManager.audioServerPorts
          } else arrayOf<Int?>(null, null)

          rtspSender.setSocketsInfo(
            socketType,
            commandsManager.protocol,
            host,
            videoClientPorts,
            audioClientPorts,
            videoServerPorts,
            audioServerPorts
          )
          rtspSender.setSocket(socket)
          rtspSender.start()
          reTries = numRetry
          onMainThread {
            connectChecker.onConnectionSuccess()
          }
          handleServerCommands()
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

  private suspend fun handleServerCommands() {
    //Read and print server commands received each 2 seconds
    while (scope.isActive && isStreaming) {
      val error = runCatching {
        if (isAlive()) {
          delay(2000)
          socket?.let { socket ->
            if (socket.isConnected()) {
              val command = commandsManager.getResponse(socket)
              //Do something depend of command if required
            }
          }
        } else {
          onMainThread {
            connectChecker.onConnectionFailed("No response from server")
          }
          scope.cancel()
        }
      }.exceptionOrNull()
      if (error != null && ConnectionFailed.parse(error.validMessage()) != ConnectionFailed.TIMEOUT) {
        scope.cancel()
      }
    }
  }

  /*
    Send a heartbeat to know if server is alive using Echo Protocol.
    Your firewall could block it.
   */
  private fun isAlive(): Boolean {
    val connected = socket?.isConnected() ?: false
    if (!checkServerAlive) return connected
    val reachable = socket?.isReachable() ?: false
    return if (connected && !reachable) false else connected
  }

  fun disconnect() {
    CoroutineScope(Dispatchers.IO).launch {
      disconnect(true)
    }
  }

  private suspend fun disconnect(clear: Boolean) {
    if (isStreaming) rtspSender.stop()
    val error = runCatching {
      withTimeoutOrNull(100) {
        socket?.write(commandsManager.createTeardown())
        socket?.flush()
      }
      socket?.close()
      socket = null
      Log.i(TAG, "write teardown success")
    }.exceptionOrNull()
    if (error != null) {
      Log.e(TAG, "disconnect error", error)
    }
    if (clear) {
      commandsManager.clear()
      reTries = numRetry
      doingRetry = false
      isStreaming = false
      onMainThread {
        connectChecker.onDisconnect()
      }
      mutex = Mutex(true)
      jobRetry?.cancelAndJoin()
      jobRetry = null
      scopeRetry.cancel()
      scopeRetry = CoroutineScope(Dispatchers.IO)
    } else {
      commandsManager.retryClear()
    }
    job?.cancelAndJoin()
    job = null
    scope.cancel()
    scope = CoroutineScope(Dispatchers.IO)
  }

  fun sendVideo(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.videoDisabled) {
      rtspSender.sendMediaFrame(MediaFrame(videoBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.VIDEO))
    }
  }

  fun sendAudio(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.audioDisabled) {
      rtspSender.sendMediaFrame(MediaFrame(audioBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.AUDIO))
    }
  }

  @JvmOverloads
  @Throws(IllegalArgumentException::class)
  fun hasCongestion(percentUsed: Float = 20f): Boolean {
    return rtspSender.hasCongestion(percentUsed)
  }

  @JvmOverloads
  fun reConnect(delay: Long, backupUrl: String? = null) {
    jobRetry = scopeRetry.launch {
      reTries--
      disconnect(false)
      delay(delay)
      val reconnectUrl = backupUrl ?: url
      connect(reconnectUrl, true)
    }
  }

  fun resetSentAudioFrames() {
    rtspSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    rtspSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    rtspSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    rtspSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    rtspSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    rtspSender.setLogs(enable)
  }

  fun clearCache() {
    rtspSender.clearCache()
  }

  fun getItemsInCache(): Int = rtspSender.getItemsInCache()

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  fun setBitrateExponentialFactor(factor: Float) {
    rtspSender.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  fun getBitrateExponentialFactor() = rtspSender.getBitrateExponentialFactor()
}