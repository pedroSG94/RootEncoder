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

import android.util.Log
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
import com.pedro.srt.srt.packets.control.handshake.extension.HandshakeExtension
import com.pedro.srt.srt.packets.control.handshake.HandshakeType
import com.pedro.srt.srt.packets.control.handshake.extension.ExtensionContentFlag
import com.pedro.srt.utils.ConnectCheckerSrt
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.onMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.regex.Pattern
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/8/23.
 */
class SrtClient(private val connectCheckerSrt: ConnectCheckerSrt) {

  private val TAG = "SrtClient"

  private val srtSender = SrtSender()
  private var socket: SrtSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null

  private var checkServerAlive = false
  @Volatile
  var isStreaming = false
    private set

  private var url: String? = null
  private val srtUrlPattern = Pattern.compile("^srt://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$")

  fun connect(url: String) {
    if (!isStreaming) {
      isStreaming = true

      job = scope.launch {
        this@SrtClient.url = url
        onMainThread {
          connectCheckerSrt.onConnectionStartedSrt(url)
        }
        val rtspMatcher = srtUrlPattern.matcher(url)
        if (!rtspMatcher.matches()) {
          isStreaming = false
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
          }
          return@launch
        }
        val host = rtspMatcher.group(1) ?: ""
        val port: Int = rtspMatcher.group(2)?.toInt() ?: 8888
        val streamName =
          if (rtspMatcher.group(4).isNullOrEmpty()) "" else "/" + rtspMatcher.group(4)
        val path = "${rtspMatcher.group(3)}$streamName".trim()

        val error = runCatching {
          socket = SrtSocket(host, port)
          socket?.connect()
          val startTs = (System.nanoTime() / 1000).toInt() //micro seconds

          val handshakeInduction = Handshake()
          var ts = (System.nanoTime() / 1000).toInt() - startTs
          handshakeInduction.write(ts, 0)
          socket?.write(handshakeInduction)

          val responseBufferInduction = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
          val responsePacketInduction = SrtPacket.getSrtPacket(responseBufferInduction)
          if (responsePacketInduction is Handshake) {
            val handshakeConclusion = responsePacketInduction.copy(
              extensionField = ExtensionField.HS_REQ.value or ExtensionField.CONFIG.value,
              handshakeType = HandshakeType.CONCLUSION,
              handshakeExtension = HandshakeExtension(
                flags = ExtensionContentFlag.TSBPDSND.value or ExtensionContentFlag.TSBPDRCV.value or
                    ExtensionContentFlag.CRYPT.value or ExtensionContentFlag.TLPKTDROP.value or
                    ExtensionContentFlag.PERIODICNAK.value or ExtensionContentFlag.REXMITFLG.value,
                path = path
              )
            )
            ts = (System.nanoTime() / 1000).toInt() - startTs
            handshakeConclusion.write(ts, 0)
            socket?.write(handshakeConclusion)
          } else {
            throw IOException("unexpected response type: ${responsePacketInduction.javaClass.name}")
          }

          val responseBufferConclusion = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
          val responsePacketConclusion = SrtPacket.getSrtPacket(responseBufferConclusion)
          if (responsePacketConclusion is Handshake) {
            if (responsePacketConclusion.handshakeType.value >= HandshakeType.SRT_REJ_UNKNOWN.value
              && responsePacketConclusion.handshakeType.value <= HandshakeType.SRT_REJ_CRYPTO.value
              ) {
              onMainThread {
                connectCheckerSrt.onConnectionFailedSrt("Error configure stream, ${responsePacketConclusion.handshakeType.name}")
              }
              return@launch
            } else {
              onMainThread {
                connectCheckerSrt.onConnectionSuccessSrt()
              }
              srtSender.start()
              handleServerPackets()
            }
          } else {
            throw IOException("unexpected response type: ${responseBufferConclusion.javaClass.name}")
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
      isStreaming = false
      val error = runCatching {
        socket?.close()
      }.exceptionOrNull()
      if (error != null) {
        Log.e(TAG, "disconnect error", error)
      }
      onMainThread {
        connectCheckerSrt.onDisconnectSrt()
      }
      job?.cancelAndJoin()
      job = null
      scope.cancel()
      scope = CoroutineScope(Dispatchers.IO)
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
        onMainThread {
          connectCheckerSrt.onConnectionFailedSrt("Error handling packet, ${error.message}")
        }
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
  private fun handleMessages() {
    val responseBufferConclusion = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
    val srtPacket = SrtPacket.getSrtPacket(responseBufferConclusion)
    when(srtPacket) {
      is DataPacket -> {
        //ignore
      }
      is ControlPacket -> {
        when (srtPacket) {
          is Handshake -> {

          }
          is KeepAlive -> {

          }
          is Ack -> {

          }
          is Nak -> {

          }
          is CongestionWarning -> {

          }
          is Shutdown -> {

          }
          is Ack2 -> {

          }
          is DropReq -> {

          }
          is PeerError -> {

          }
        }
      }
    }
  }
}