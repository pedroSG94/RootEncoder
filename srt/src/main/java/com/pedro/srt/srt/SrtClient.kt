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

package com.pedro.srt.srt

import android.media.MediaCodec
import android.util.Log
import com.pedro.srt.mpeg2ts.Codec
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
import com.pedro.srt.srt.packets.control.handshake.ExtensionField
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.srt.packets.control.handshake.HandshakeType
import com.pedro.srt.srt.packets.control.handshake.extension.ExtensionContentFlag
import com.pedro.srt.srt.packets.control.handshake.extension.HandshakeExtension
import com.pedro.srt.utils.ConnectCheckerSrt
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.onMainThread
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
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 * Created by pedro on 20/8/23.
 */
class SrtClient(private val connectCheckerSrt: ConnectCheckerSrt) {

  private val TAG = "SrtClient"
  private val srtUrlPattern = Pattern.compile("^srt://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$")

  private val commandsManager = CommandsManager()
  private val srtSender = SrtSender(connectCheckerSrt, commandsManager)
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
      srtSender.videoCodec = if (videoCodec == VideoCodec.H265) Codec.HEVC else Codec.AVC
    }
  }

  fun setAuthorization(user: String?, password: String?) {
    TODO("unimplemented")
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
            connectCheckerSrt.onConnectionFailedSrt("Endpoint malformed, should be: srt://ip:port/streamid")
          }
          return@launch
        }
        this@SrtClient.url = url
        onMainThread {
          connectCheckerSrt.onConnectionStartedSrt(url)
        }
        val srtMatcher = srtUrlPattern.matcher(url)
        if (!srtMatcher.matches()) {
          isStreaming = false
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("Endpoint malformed, should be: srt://ip:port/streamid")
          }
          return@launch
        }
        val host = srtMatcher.group(1) ?: ""
        val port: Int = srtMatcher.group(2)?.toInt() ?: 8888
        val streamName =
          if (srtMatcher.group(4).isNullOrEmpty()) "" else "/" + srtMatcher.group(4)
        val path = "${srtMatcher.group(3)}$streamName".trim()

        val error = runCatching {
          socket = SrtSocket(host, port)
          socket?.connect()
          commandsManager.loadStartTs()

          commandsManager.writeHandshake(socket)
          val response = commandsManager.readHandshake(socket)

          commandsManager.writeHandshake(socket, response.copy(
            extensionField = ExtensionField.HS_REQ.value or ExtensionField.CONFIG.value,
            handshakeType = HandshakeType.CONCLUSION,
            handshakeExtension = HandshakeExtension(
              flags = ExtensionContentFlag.TSBPDSND.value or ExtensionContentFlag.TSBPDRCV.value or
                  ExtensionContentFlag.CRYPT.value or ExtensionContentFlag.TLPKTDROP.value or
                  ExtensionContentFlag.PERIODICNAK.value or ExtensionContentFlag.REXMITFLG.value,
              path = path
            )))
          val responseConclusion = commandsManager.readHandshake(socket)
          if (responseConclusion.isErrorType()) {
            onMainThread {
              connectCheckerSrt.onConnectionFailedSrt("Error configure stream, ${responseConclusion.handshakeType.name}")
            }
            return@launch
          } else {
            commandsManager.socketId = responseConclusion.srtSocketId
            commandsManager.MTU = responseConclusion.MTU
            commandsManager.sequenceNumber = responseConclusion.initialPacketSequence
            onMainThread {
              connectCheckerSrt.onConnectionSuccessSrt()
            }
            srtSender.socket = socket
            srtSender.start()
            handleServerPackets()
          }
        }.exceptionOrNull()
        if (error != null) {
          Log.e(TAG, "connection error", error)
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("Error configure stream, ${error.message}")
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
    if (isStreaming) srtSender.stop()
    val error = runCatching {
      commandsManager.writeShutdown(socket)
      socket?.close()
    }.exceptionOrNull()
    if (error != null) {
      Log.e(TAG, "disconnect error", error)
    }
    if (clear) {
      reTries = numRetry
      doingRetry = false
      isStreaming = false
      onMainThread {
        connectCheckerSrt.onDisconnectSrt()
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

  @Throws(IOException::class)
  private suspend fun handleServerPackets() {
    while (scope.isActive && isStreaming) {
      val error = runCatching {
        if (isAlive()) {
          //ignore packet after connect if tunneled to avoid spam idle
          handleMessages()
        } else {
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("No response from server")
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
    val srtPacket = SrtPacket.getSrtPacket(responseBufferConclusion)
    when(srtPacket) {
      is DataPacket -> {
        //ignore
      }
      is ControlPacket -> {
        when (srtPacket) {
          is Handshake -> {
            //never should happens, handshake is already done
          }
          is KeepAlive -> {

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
              connectCheckerSrt.onConnectionFailedSrt("Shutdown received from server")
            }
          }
          is Ack2 -> {
            //never should happens
          }
          is DropReq -> {

          }
          is PeerError -> {
            val reason = srtPacket.errorCode
            connectCheckerSrt.onConnectionFailedSrt("PeerError: $reason")
          }
        }
      }
    }
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    srtSender.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
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

  fun hasCongestion(): Boolean {
    return srtSender.hasCongestion()
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
}