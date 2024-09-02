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

package com.pedro.rtmp.rtmp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.TimeUtils
import com.pedro.common.UrlParser
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThread
import com.pedro.rtmp.amf.AmfVersion
import com.pedro.rtmp.rtmp.message.*
import com.pedro.rtmp.rtmp.message.command.Command
import com.pedro.rtmp.rtmp.message.control.Type
import com.pedro.rtmp.rtmp.message.control.UserControl
import com.pedro.rtmp.utils.AuthUtil
import com.pedro.rtmp.utils.RtmpConfig
import com.pedro.rtmp.utils.socket.RtmpSocket
import com.pedro.rtmp.utils.socket.TcpSocket
import com.pedro.rtmp.utils.socket.TcpTunneledSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 8/04/21.
 */
class RtmpClient(private val connectChecker: ConnectChecker) {

  private val TAG = "RtmpClient"

  private val validSchemes = arrayOf("rtmp", "rtmps", "rtmpt", "rtmpts")

  private var socket: RtmpSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var scopeRetry = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  private var jobRetry: Job? = null
  private var commandsManager: CommandsManager = CommandsManagerAmf0()
  private val rtmpSender = RtmpSender(connectChecker, commandsManager)

  @Volatile
  var isStreaming = false
    private set

  private var url: String? = null
  private var tlsEnabled = false
  private var certificates: Array<TrustManager>? = null
  private var tunneled = false

  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0
  private var checkServerAlive = false
  private var publishPermitted = false

  val droppedAudioFrames: Long
    get() = rtmpSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = rtmpSender.droppedVideoFrames

  val cacheSize: Int
    get() = rtmpSender.getCacheSize()
  val sentAudioFrames: Long
    get() = rtmpSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = rtmpSender.getSentVideoFrames()

  /**
   * Add certificates for TLS connection
   */
  fun addCertificates(certificates: Array<TrustManager>?) {
    this.certificates = certificates
  }

  fun setVideoCodec(videoCodec: VideoCodec) {
    if (!isStreaming) {
      commandsManager.videoCodec = videoCodec
    }
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    if (!isStreaming) {
      commandsManager.audioCodec = when (audioCodec) {
        AudioCodec.OPUS -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
        else -> audioCodec
      }
    }
  }

  fun setAmfVersion(amfVersion: AmfVersion) {
    if (!isStreaming) {
      commandsManager = when (amfVersion) {
        AmfVersion.VERSION_0 -> CommandsManagerAmf0()
        AmfVersion.VERSION_3 -> CommandsManagerAmf3()
      }
    }
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
    commandsManager.audioDisabled = false
    commandsManager.videoDisabled = onlyAudio
  }

  /**
   * Must be called before connect
   */
  fun setOnlyVideo(onlyVideo: Boolean) {
    commandsManager.videoDisabled = false
    commandsManager.audioDisabled = onlyVideo
  }

  fun forceIncrementalTs(enabled: Boolean) {
    commandsManager.incrementalTs = enabled
  }

  fun setWriteChunkSize(chunkSize: Int) {
    if (!isStreaming) {
      RtmpConfig.writeChunkSize = chunkSize
    }
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

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    commandsManager.setAudioInfo(sampleRate, isStereo)
    rtmpSender.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    rtmpSender.setVideoInfo(sps, pps, vps)
  }

  fun setVideoResolution(width: Int, height: Int) {
    commandsManager.setVideoResolution(width, height)
  }

  fun setFps(fps: Int) {
    commandsManager.fps = fps
  }

  fun setFlashVersion(flashVersion: String) {
    commandsManager.flashVersion = flashVersion
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
            connectChecker.onConnectionFailed(
              "Endpoint malformed, should be: rtmp://ip:port/appname/streamname"
            )
          }
          return@launch
        }
        this@RtmpClient.url = url
        onMainThread {
          connectChecker.onConnectionStarted(url)
        }

        val urlParser = try {
          UrlParser.parse(url, validSchemes)
        } catch (e: URISyntaxException) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed(
              "Endpoint malformed, should be: rtmp://ip:port/appname/streamname")
          }
          return@launch
        }

        tunneled = urlParser.scheme.startsWith("rtmpt")
        tlsEnabled = urlParser.scheme.endsWith("s")
        commandsManager.host = urlParser.host
        val defaultPort = if (tlsEnabled) 443 else if (tunneled) 80 else 1935
        commandsManager.port = urlParser.port ?: defaultPort
        commandsManager.appName = urlParser.getAppName()
        commandsManager.streamName = urlParser.getStreamName()
        commandsManager.tcUrl = urlParser.getTcUrl()
        if (commandsManager.appName.isEmpty()) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed(
              "Endpoint malformed, should be: rtmp://ip:port/appname/streamname")
          }
          return@launch
        }

        val user = urlParser.getAuthUser()
        val password = urlParser.getAuthPassword()
        if (user != null && password != null) setAuthorization(user, password)

        val error = runCatching {
          if (!establishConnection()) {
            onMainThread {
              connectChecker.onConnectionFailed("Handshake failed")
            }
            return@launch
          }
          val socket = this@RtmpClient.socket ?: throw IOException("Invalid socket, Connection failed")
          commandsManager.sendChunkSize(socket)
          commandsManager.sendConnect("", socket)
          //read packets until you did success connection to server and you are ready to send packets
          while (scope.isActive && !publishPermitted) {
            //Handle all command received and send response for it.
            handleMessages()
          }
          //read packet because maybe server want send you something while streaming
          handleServerPackets()
        }.exceptionOrNull()
        if (error != null) {
          Log.e(TAG, "connection error", error)
          onMainThread {
            connectChecker.onConnectionFailed("Error configure stream, ${error.message}")
          }
          return@launch
        }
      }
    }
  }

  private suspend fun handleServerPackets() {
    while (scope.isActive && isStreaming) {
      val error = runCatching {
        if (isAlive()) {
          //ignore packet after connect if tunneled to avoid spam idle
          if (!tunneled) handleMessages()
        } else {
          onMainThread {
            connectChecker.onConnectionFailed("No response from server")
          }
          scope.cancel()
        }
      }.exceptionOrNull()
      if (error != null && error !is SocketTimeoutException) {
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

  @Throws(IOException::class)
  private fun establishConnection(): Boolean {
    val socket = if (tunneled) {
      TcpTunneledSocket(commandsManager.host, commandsManager.port, tlsEnabled)
    } else {
      TcpSocket(commandsManager.host, commandsManager.port, tlsEnabled, certificates)
    }
    this.socket = socket
    socket.connect()
    if (!socket.isConnected()) return false
    val timestamp = TimeUtils.getCurrentTimeMillis() / 1000
    val handshake = Handshake()
    if (!handshake.sendHandshake(socket)) return false
    commandsManager.timestamp = timestamp.toInt()
    commandsManager.startTs = TimeUtils.getCurrentTimeNano() / 1000
    return true
  }

  /**
   * Read all messages from server and response to it
   */
  @Throws(IOException::class)
  private suspend fun handleMessages() {
    var socket = this.socket ?: throw IOException("Invalid socket, Connection failed")

    val message = commandsManager.readMessageResponse(socket)
    commandsManager.checkAndSendAcknowledgement(socket)
    when (message.getType()) {
      MessageType.SET_CHUNK_SIZE -> {
        val setChunkSize = message as SetChunkSize
        commandsManager.readChunkSize = setChunkSize.chunkSize
        Log.i(TAG, "chunk size configured to ${setChunkSize.chunkSize}")
      }
      MessageType.ACKNOWLEDGEMENT -> {
        val acknowledgement = message as Acknowledgement
      }
      MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> {
        val windowAcknowledgementSize = message as WindowAcknowledgementSize
        RtmpConfig.acknowledgementWindowSize = windowAcknowledgementSize.acknowledgementWindowSize
      }
      MessageType.SET_PEER_BANDWIDTH -> {
        val setPeerBandwidth = message as SetPeerBandwidth
        commandsManager.sendWindowAcknowledgementSize(socket)
      }
      MessageType.ABORT -> {
        val abort = message as Abort
      }
      MessageType.AGGREGATE -> {
        val aggregate = message as Aggregate
      }
      MessageType.USER_CONTROL -> {
        val userControl = message as UserControl
        when (val type = userControl.type) {
          Type.PING_REQUEST -> {
            commandsManager.sendPong(userControl.event, socket)
          }
          else -> {
            Log.i(TAG, "user control command $type ignored")
          }
        }
      }
      MessageType.COMMAND_AMF0, MessageType.COMMAND_AMF3 -> {
        val command = message as Command
        val commandName = commandsManager.sessionHistory.getName(command.commandId)
        when (command.name) {
          "_result" -> {
            when (commandName) {
              "connect" -> {
                if (commandsManager.onAuth) {
                  onMainThread {
                    connectChecker.onAuthSuccess()
                  }
                  commandsManager.onAuth = false
                }
                commandsManager.createStream(socket)
              }
              "createStream" -> {
                try {
                  commandsManager.streamId = command.getStreamId()
                  commandsManager.sendPublish(socket)
                } catch (e: ClassCastException) {
                  Log.e(TAG, "error parsing _result createStream", e)
                }
              }
            }
            Log.i(TAG, "success response received from ${commandName ?: "unknown command"}")
          }
          "_error" -> {
            try {
              val description = command.getDescription()
              when (commandName) {
                "connect" -> {
                  if (description.contains("reason=authfail") || description.contains("reason=nosuchuser")) {
                    onMainThread {
                      connectChecker.onAuthError()
                    }
                  } else if (commandsManager.user != null && commandsManager.password != null &&
                      description.contains("challenge=") && description.contains("salt=") //adobe response
                      || description.contains("nonce=")) { //llnw response
                    closeConnection()
                    establishConnection()
                    socket = this.socket ?: throw IOException("Invalid socket, Connection failed")
                    commandsManager.onAuth = true
                    if (description.contains("challenge=") && description.contains("salt=")) { //create adobe auth
                      val salt = AuthUtil.getSalt(description)
                      val challenge = AuthUtil.getChallenge(description)
                      val opaque = AuthUtil.getOpaque(description)
                      commandsManager.sendConnect(AuthUtil.getAdobeAuthUserResult(commandsManager.user
                          ?: "", commandsManager.password ?: "",
                          salt, challenge, opaque), socket)
                    } else if (description.contains("nonce=")) { //create llnw auth
                      val nonce = AuthUtil.getNonce(description)
                      commandsManager.sendConnect(AuthUtil.getLlnwAuthUserResult(commandsManager.user
                          ?: "", commandsManager.password ?: "",
                          nonce, commandsManager.appName), socket)
                    }
                  } else if (description.contains("code=403")) {
                    if (description.contains("authmod=adobe")) {
                      closeConnection()
                      establishConnection()
                      socket = this.socket ?: throw IOException("Invalid socket, Connection failed")
                      Log.i(TAG, "sending auth mode adobe")
                      commandsManager.sendConnect("?authmod=adobe&user=${commandsManager.user}", socket)
                    } else if (description.contains("authmod=llnw")) {
                      Log.i(TAG, "sending auth mode llnw")
                      commandsManager.sendConnect("?authmod=llnw&user=${commandsManager.user}", socket)
                    }
                  } else {
                    onMainThread {
                      connectChecker.onAuthError()
                    }
                  }
                }
                //We can ignore this errors. Some servers fail if this stream is not in use or don't implement this methods.
                "releaseStream", "FCPublish" -> {
                  Log.e(TAG, "$commandName failed: $description")
                }
                else -> {
                  onMainThread {
                    connectChecker.onConnectionFailed(description)
                  }
                }
              }
            } catch (e: ClassCastException) {
              Log.e(TAG, "error parsing _error command", e)
            }
          }
          "onStatus" -> {
            try {
              when (val code = command.getCode()) {
                "NetStream.Publish.Start" -> {
                  commandsManager.sendMetadata(socket)
                  onMainThread {
                    connectChecker.onConnectionSuccess()
                  }
                  rtmpSender.socket = socket
                  rtmpSender.start()
                  publishPermitted = true
                }
                "NetConnection.Connect.Rejected", "NetStream.Publish.BadName" -> {
                  onMainThread {
                    connectChecker.onConnectionFailed("onStatus: $code")
                  }
                }
                else -> {
                  Log.i(TAG, "onStatus $code response received from ${commandName ?: "unknown command"}")
                }
              }
            } catch (e: ClassCastException) {
              Log.e(TAG, "error parsing onStatus command", e)
            }
          }
          else -> {
            Log.i(TAG, "unknown ${command.name} response received from ${commandName ?: "unknown command"}")
          }
        }
      }
      MessageType.VIDEO, MessageType.AUDIO, MessageType.DATA_AMF0, MessageType.DATA_AMF3,
      MessageType.SHARED_OBJECT_AMF0, MessageType.SHARED_OBJECT_AMF3 -> {
        Log.e(TAG, "unimplemented response for ${message.getType()}. Ignored")
      }
    }
  }

  fun closeConnection() {
    socket?.close()
    commandsManager.reset()
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

  fun disconnect() {
    CoroutineScope(Dispatchers.IO).launch {
      disconnect(true)
    }
  }

  private suspend fun disconnect(clear: Boolean) {
    if (isStreaming) rtmpSender.stop(clear)
    runCatching {
      socket?.let { socket ->
        commandsManager.sendClose(socket)
      }
    }
    closeConnection()
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
    job?.cancelAndJoin()
    job = null
    scope.cancel()
    scope = CoroutineScope(Dispatchers.IO)
    publishPermitted = false
    commandsManager.reset()
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.videoDisabled) {
      rtmpSender.sendVideoFrame(h264Buffer, info)
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.audioDisabled) {
      rtmpSender.sendAudioFrame(aacBuffer, info)
    }
  }

  @JvmOverloads
  @Throws(IllegalArgumentException::class)
  fun hasCongestion(percentUsed: Float = 20f): Boolean {
    return rtmpSender.hasCongestion(percentUsed)
  }

  fun resetSentAudioFrames() {
    rtmpSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    rtmpSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    rtmpSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    rtmpSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    rtmpSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    rtmpSender.setLogs(enable)
  }

  fun clearCache() {
    rtmpSender.clearCache()
  }

  fun getItemsInCache(): Int = rtmpSender.getItemsInCache()

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  fun setBitrateExponentialFactor(factor: Float) {
    rtmpSender.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  fun getBitrateExponentialFactor() = rtmpSender.getBitrateExponentialFactor()
}
