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

package com.pedro.srt.srt

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.UrlParser
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThread
import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.control.Ack
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.CongestionWarning
import com.pedro.srt.srt.packets.control.DropReq
import com.pedro.srt.srt.packets.control.KeepAlive
import com.pedro.srt.srt.packets.control.Nak
import com.pedro.srt.srt.packets.control.PeerError
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.EncryptionType
import com.pedro.srt.srt.packets.control.handshake.ExtensionField
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.srt.packets.control.handshake.HandshakeType
import com.pedro.srt.srt.packets.control.handshake.extension.ExtensionContentFlag
import com.pedro.srt.srt.packets.control.handshake.extension.HandshakeExtension
import com.pedro.srt.utils.SrtSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URISyntaxException
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class SrtClient(private val connectChecker: ConnectChecker) {

  private val TAG = "SrtClient"

  private val validSchemes = arrayOf("srt")

  private val commandsManager = CommandsManager()
  private val srtSender = SrtSender(connectChecker, commandsManager)
  private var socket: SrtSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  private var scopeRetry = CoroutineScope(Dispatchers.IO)
  private var jobRetry: Job? = null

  private var checkServerAlive = false
  @Volatile
  var isStreaming = false
    private set
  private var url: String? = null
  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0

  val droppedAudioFrames: Long
    get() = srtSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = srtSender.droppedVideoFrames

  val cacheSize: Int
    get() = srtSender.getCacheSize()
  val sentAudioFrames: Long
    get() = srtSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = srtSender.getSentVideoFrames()

  fun setVideoCodec(videoCodec: VideoCodec) {
    if (!isStreaming) {
      commandsManager.videoCodec = when (videoCodec) {
        VideoCodec.AV1 -> throw IllegalArgumentException("Unsupported codec: ${videoCodec.name}")
        else -> videoCodec
      }
    }
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    if (!isStreaming) {
      commandsManager.audioCodec = when (audioCodec) {
        AudioCodec.G711 -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
        else -> audioCodec
      }
    }
  }

  fun setAuthorization(user: String?, password: String?) {
    TODO("unimplemented")
  }

  /**
   * Set passphrase for encrypt. Use empty value to disable it.
   */
  fun setPassphrase(passphrase: String, type: EncryptionType) {
    if (!isStreaming) {
      if (passphrase.length < 10 || passphrase.length > 79) {
        throw IllegalArgumentException("passphrase must between 10 and 79 length")
      }
      commandsManager.setPassphrase(passphrase, type)
    }
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

  /**
   * Check periodically if server is alive using Echo protocol.
   */
  fun setCheckServerAlive(enabled: Boolean) {
    checkServerAlive = enabled
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
            connectChecker.onConnectionFailed("Endpoint malformed, should be: srt://ip:port/streamid")
          }
          return@launch
        }
        this@SrtClient.url = url
        onMainThread {
          connectChecker.onConnectionStarted(url)
        }

        val urlParser = try {
          UrlParser.parse(url, validSchemes)
        } catch (e: URISyntaxException) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: srt://ip:port/streamid")
          }
          return@launch
        }

        val host = urlParser.host
        val port = urlParser.port ?: 8888
        val path = urlParser.getQuery("streamid") ?: urlParser.getFullPath()
        if (path.isEmpty()) {
          isStreaming = false
          onMainThread {
            connectChecker.onConnectionFailed("Endpoint malformed, should be: srt://ip:port/streamid")
          }
          return@launch
        }
        commandsManager.host = host

        val error = runCatching {
          socket = SrtSocket(host, port)
          socket?.connect()
          commandsManager.loadStartTs()

          commandsManager.writeHandshake(socket)
          val response = commandsManager.readHandshake(socket)

          commandsManager.writeHandshake(socket, response.copy(
            encryption = commandsManager.getEncryptType(),
            extensionField = ExtensionField.HS_REQ.value or ExtensionField.CONFIG.value,
            handshakeType = HandshakeType.CONCLUSION,
            handshakeExtension = HandshakeExtension(
              flags = ExtensionContentFlag.TSBPDSND.value or ExtensionContentFlag.TSBPDRCV.value or
                  ExtensionContentFlag.CRYPT.value or ExtensionContentFlag.TLPKTDROP.value or
                  ExtensionContentFlag.PERIODICNAK.value or ExtensionContentFlag.REXMITFLG.value,
              path = path,
              encryptInfo = commandsManager.getEncryptInfo()
            )))
          val responseConclusion = commandsManager.readHandshake(socket)
          if (responseConclusion.isErrorType()) {
            onMainThread {
              connectChecker.onConnectionFailed("Error configure stream, ${responseConclusion.handshakeType.name}")
            }
            return@launch
          } else {
            commandsManager.socketId = responseConclusion.srtSocketId
            commandsManager.MTU = responseConclusion.MTU
            commandsManager.sequenceNumber = responseConclusion.initialPacketSequence
            onMainThread {
              connectChecker.onConnectionSuccess()
            }
            srtSender.socket = socket
            srtSender.start()
            handleServerPackets()
          }
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

  fun disconnect() {
    CoroutineScope(Dispatchers.IO).launch {
      disconnect(true)
    }
  }

  private suspend fun disconnect(clear: Boolean) {
    if (isStreaming) srtSender.stop(clear)
    runCatching {
      commandsManager.writeShutdown(socket)
    }
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
    commandsManager.reset()
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

  @Throws(IOException::class)
  private suspend fun handleServerPackets() {
    while (scope.isActive && isStreaming) {
      val error = runCatching {
        if (isAlive()) {
          //ignore packet after connect if tunneled to avoid spam idle
          handleMessages()
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
    if (!checkServerAlive) {
      return connected
    }
    val reachable = socket?.isReachable() ?: false
    return if (connected && !reachable) false else connected
  }

  @Throws(IOException::class)
  private suspend fun handleMessages() {
    val responseBufferConclusion = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
    when(val srtPacket = SrtPacket.getSrtPacket(responseBufferConclusion)) {
      is DataPacket -> {
        //ignore
      }
      is ControlPacket -> {
        when (srtPacket) {
          is Handshake -> {
            //never should happens, handshake is already done
          }
          is KeepAlive -> {
            commandsManager.writeKeepAlive(socket)
          }
          is Ack -> {
            val ackSequence = srtPacket.typeSpecificInformation
            val lastPacketSequence = srtPacket.lastAcknowledgedPacketSequenceNumber
            commandsManager.updateHandlingQueue(lastPacketSequence)
            commandsManager.writeAck2(ackSequence, socket)
          }
          is Nak -> {
            //packet lost reported, we should resend it
            val packetsLost = srtPacket.getNakPacketsLostList()
            commandsManager.reSendPackets(packetsLost, socket)
          }
          is CongestionWarning -> {

          }
          is Shutdown -> {
            onMainThread {
              connectChecker.onConnectionFailed("Shutdown received from server")
            }
          }
          is Ack2 -> {
            //never should happens
          }
          is DropReq -> {

          }
          is PeerError -> {
            val reason = srtPacket.errorCode
            onMainThread {
              connectChecker.onConnectionFailed("PeerError: $reason")
            }
          }
        }
      }
    }
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    srtSender.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    srtSender.setVideoInfo(sps, pps, vps)
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.videoDisabled) {
      srtSender.sendVideoFrame(h264Buffer, info)
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.audioDisabled) {
      srtSender.sendAudioFrame(aacBuffer, info)
    }
  }

  @Throws(IllegalArgumentException::class)
  fun hasCongestion(): Boolean {
    return hasCongestion(20f)
  }

  @Throws(IllegalArgumentException::class)
  fun hasCongestion(percentUsed: Float): Boolean {
    return srtSender.hasCongestion(percentUsed)
  }

  fun resetSentAudioFrames() {
    srtSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    srtSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    srtSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    srtSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    srtSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    srtSender.setLogs(enable)
  }

  fun clearCache() {
    srtSender.clearCache()
  }

  fun getItemsInCache(): Int = srtSender.getItemsInCache()

  /**
   * @param factor values from 0.1f to 1f
   * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
   */
  fun setBitrateExponentialFactor(factor: Float) {
    srtSender.setBitrateExponentialFactor(factor)
  }

  /**
   * Get the exponential factor used to calculate the bitrate. Default 1f
   */
  fun getBitrateExponentialFactor() = srtSender.getBitrateExponentialFactor()
}