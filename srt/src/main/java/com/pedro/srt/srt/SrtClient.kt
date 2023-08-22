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
import com.pedro.srt.srt.packets.control.ExtensionType
import com.pedro.srt.srt.packets.control.Handshake
import com.pedro.srt.srt.packets.control.HandshakeExtension
import com.pedro.srt.srt.packets.control.HandshakeType
import com.pedro.srt.utils.ConnectCheckerSrt
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.onMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.regex.Pattern

/**
 * Created by pedro on 20/8/23.
 */
class SrtClient(private val connectCheckerSrt: ConnectCheckerSrt) {

  private val TAG = "SrtClient"

  private var socket: DatagramSocket? = null
  private var scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null

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
        val path = "/" + rtspMatcher.group(3) + streamName

        val error = runCatching {
          val address = InetAddress.getByName(host)
          socket = DatagramSocket(port)
          socket?.connect(address, port)
          val startTs = (System.currentTimeMillis() / 1000).toInt()

          val handshakeInduction = Handshake()
          var ts = (System.currentTimeMillis() / 1000).toInt() - startTs
          handshakeInduction.write(ts, 0)
          val bytes1 = handshakeInduction.getData()
          val packet1 = DatagramPacket(bytes1, bytes1.size)
          socket?.send(packet1)

          val readCache = ByteArray(Constants.MTU)
          val response = DatagramPacket(readCache, Constants.MTU)
          socket?.receive(response)
          val responseHS = Handshake()
          responseHS.read(response.data.sliceArray(0 until response.length))

          val handshakeConclusion = responseHS.copy(
            handshakeType = HandshakeType.CONCLUSION,
            extensionType = ExtensionType.SRT_CMD_HS_REQ,
            extensionLength = 3,
            handshakeExtension = HandshakeExtension() //TODO
          )
          ts = (System.currentTimeMillis() / 1000).toInt() - startTs
          handshakeConclusion.write(ts, 0)
          val bytes = handshakeConclusion.getData()
          val packet = DatagramPacket(bytes, bytes.size)
          socket?.send(packet)

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
}