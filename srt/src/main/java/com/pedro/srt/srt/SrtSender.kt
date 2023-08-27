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
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.packets.H264Packet
import com.pedro.srt.mpeg2ts.psi.PSIManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.utils.BitrateManager
import com.pedro.srt.utils.ConnectCheckerSrt
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.onMainThread
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
 * Created by pedro on 20/8/23.
 */
class SrtSender(
  private val connectCheckerSrt: ConnectCheckerSrt,
  private val commandsManager: CommandsManager
) {

  private val service = Mpeg2TsService()
  private val psiManager = PSIManager(service)
  private val aacPacket = AacPacket(188 * 7, psiManager)
  private val h264Packet = H264Packet(commandsManager.MTU - SrtPacket.headerSize, psiManager)
  @Volatile
  private var running = false

  private var cacheSize = 60
  private var itemsInQueue = 0

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  private var queue = Channel<MpegTsPacket>(cacheSize)
  private var queueFlow = queue.receiveAsFlow()
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: SrtSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
//  var videoCodec = VideoCodec.H264
  private val bitrateManager: BitrateManager = BitrateManager(connectCheckerSrt)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "RtmpSender"
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
//    service.addTrack(Codec.AVC, 0)
    h264Packet.sendVideoInfo(sps, pps)
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    service.addTrack(Codec.AAC, 0)
    psiManager.upgradePatVersion()
    psiManager.upgradeSdtVersion()
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      h264Packet.createAndSendPacket(h264Buffer, info) { mpegTsPacket ->
        val error = queue.trySend(mpegTsPacket).exceptionOrNull()
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
    if (running) {
      aacPacket.createAndSendPacket(aacBuffer, info) { mpegTsPacket ->
        val error = queue.trySend(mpegTsPacket).exceptionOrNull()
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
    running = true
    job = scope.launch {
      queueFlow.collect { mpegTsPacket ->
        itemsInQueue--
        val error = runCatching {
          var size = 0
          Log.i(TAG, "tsSize: ${mpegTsPacket.buffer.size}")
          size += commandsManager.writeData(mpegTsPacket, socket)
          if (isEnableLogs) {
            if (mpegTsPacket.isVideo) {
              Log.i(TAG, "wrote Video packet, size $size")
            } else {
              Log.i(TAG, "wrote Audio packet, size $size")
            }
          }
          //bytes to bits
          bitrateManager.calculateBitrate(size * 8L)
        }.exceptionOrNull()
        if (error != null) {
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("Error send packet, " + error.message)
          }
          Log.e(TAG, "send error: ", error)
          return@collect
        }
      }
    }
  }

  suspend fun stop() {
    running = false
    queue.cancel()
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    //TODO reset packets if needed
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
      val tempQueue = Channel<MpegTsPacket>(newSize)
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