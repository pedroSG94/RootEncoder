/*
 * Copyright (C) 2021 pedroSG94.
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
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AacPacket
import com.pedro.rtmp.flv.video.H264Packet
import com.pedro.rtmp.flv.video.ProfileIop
import com.pedro.rtmp.utils.BitrateManager
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtmp.utils.onMainThread
import com.pedro.rtmp.utils.socket.RtmpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/04/21.
 */
class RtmpSender(
  private val connectCheckerRtmp: ConnectCheckerRtmp,
  private val commandsManager: CommandsManager
) {

  private var aacPacket = AacPacket()
  private var h264Packet = H264Packet()

  private var cacheSize = 60
  private var itemsInQueue = 0

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  private var queue = Channel<FlvPacket>(cacheSize)
  private var queueFlow = queue.receiveAsFlow()
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: RtmpSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
  private val bitrateManager: BitrateManager = BitrateManager(connectCheckerRtmp)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "RtmpSender"
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    h264Packet.sendVideoInfo(sps, pps)
  }

  fun setProfileIop(profileIop: ProfileIop) {
    h264Packet.profileIop = profileIop
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (scope.isActive) {
      h264Packet.createFlvVideoPacket(h264Buffer, info) { flvPacket ->
        val error = queue.trySend(flvPacket).exceptionOrNull()
        if (error != null) {
          Log.i(TAG, "Video frame discarded")
          droppedVideoFrames++
        } else {
          itemsInQueue++
        }
      }
    }
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (scope.isActive) {
      aacPacket.createFlvAudioPacket(aacBuffer, info) { flvPacket ->
        val error = queue.trySend(flvPacket).exceptionOrNull()
        if (error != null) {
          Log.i(TAG, "Audio frame discarded")
          droppedAudioFrames++
        } else {
          itemsInQueue++
        }
      }
    }
  }

  fun start() {
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    job = scope.launch {
      queueFlow.collect { flvPacket ->
        itemsInQueue--
        val error = runCatching {
          var size = 0
          if (flvPacket.type == FlvType.VIDEO) {
            videoFramesSent++
            socket?.let { socket ->
              size = commandsManager.sendVideoPacket(flvPacket, socket)
              if (isEnableLogs) {
                Log.i(TAG, "wrote Video packet, size $size")
              }
            }
          } else {
            audioFramesSent++
            socket?.let { socket ->
              size = commandsManager.sendAudioPacket(flvPacket, socket)
              if (isEnableLogs) {
                Log.i(TAG, "wrote Audio packet, size $size")
              }
            }
          }
          //bytes to bits
          bitrateManager.calculateBitrate(size * 8L)
        }.exceptionOrNull()
        if (error != null) {
          onMainThread {
            connectCheckerRtmp.onConnectionFailedRtmp("Error send packet, " + error.message)
          }
          Log.e(TAG, "send error: ", error)
        }
      }
    }
  }

  suspend fun stop(clear: Boolean = true) {
    queue.cancel()
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    aacPacket.reset()
    h264Packet.reset(clear)
    resetSentAudioFrames()
    resetSentVideoFrames()
    resetDroppedAudioFrames()
    resetDroppedVideoFrames()
    job?.cancelAndJoin()
    job = null
  }

  fun hasCongestion(): Boolean {
    val size = cacheSize
    val remaining = cacheSize - itemsInQueue
    val capacity = size + remaining
    return size >= capacity * 0.2f //more than 20% queue used. You could have congestion
  }

  fun resizeCache(newSize: Int) {
    if (!scope.isActive) {
      val tempQueue = Channel<FlvPacket>(newSize)
      queue = tempQueue
      queueFlow = queue.receiveAsFlow()
      cacheSize = newSize
    }
  }

  fun getCacheSize(): Int {
    return cacheSize
  }

  fun getSentAudioFrames(): Long {
    return audioFramesSent
  }

  fun getSentVideoFrames(): Long {
    return videoFramesSent
  }

  fun resetSentAudioFrames() {
    audioFramesSent = 0
  }

  fun resetSentVideoFrames() {
    videoFramesSent = 0
  }

  fun resetDroppedAudioFrames() {
    droppedAudioFrames = 0
  }

  fun resetDroppedVideoFrames() {
    droppedVideoFrames = 0
  }

  fun setLogs(enable: Boolean) {
    isEnableLogs = enable
  }
}