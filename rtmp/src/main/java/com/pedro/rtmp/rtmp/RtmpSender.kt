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
import com.pedro.common.BitrateManager
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThread
import com.pedro.common.trySend
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.packet.AacPacket
import com.pedro.rtmp.flv.audio.packet.G711Packet
import com.pedro.rtmp.flv.video.packet.Av1Packet
import com.pedro.rtmp.flv.video.packet.H264Packet
import com.pedro.rtmp.flv.video.packet.H265Packet
import com.pedro.rtmp.utils.socket.RtmpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by pedro on 8/04/21.
 */
class RtmpSender(
  private val connectChecker: ConnectChecker,
  private val commandsManager: CommandsManager
) {

  private var audioPacket: BasePacket = AacPacket()
  private var videoPacket: BasePacket = H264Packet()
  @Volatile
  private var running = false
  private var cacheSize = 200

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  @Volatile
  private var queue: BlockingQueue<FlvPacket> = LinkedBlockingQueue(cacheSize)
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: RtmpSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
  private val bitrateManager: BitrateManager = BitrateManager(connectChecker)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "RtmpSender"
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    when (commandsManager.videoCodec) {
      VideoCodec.H265 -> {
        if (vps == null || pps == null) throw IllegalArgumentException("pps or vps can't be null with h265")
        videoPacket = H265Packet()
        (videoPacket as H265Packet).sendVideoInfo(sps, pps, vps)
      }
      VideoCodec.AV1 -> {
        videoPacket = Av1Packet()
        (videoPacket as Av1Packet).sendVideoInfo(sps)
      }
      else -> {
        if (pps == null) throw IllegalArgumentException("pps can't be null with h264")
        videoPacket = H264Packet()
        (videoPacket as H264Packet).sendVideoInfo(sps, pps)
      }
    }
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    when (commandsManager.audioCodec) {
      AudioCodec.G711 -> {
        audioPacket = G711Packet()
        (audioPacket as G711Packet).sendAudioInfo()
      }
      AudioCodec.AAC -> {
        audioPacket = AacPacket()
        (audioPacket as AacPacket).sendAudioInfo(sampleRate, isStereo)
      }
      AudioCodec.OPUS -> {
        throw IllegalArgumentException("Unsupported codec: ${commandsManager.audioCodec.name}")
      }
    }
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      videoPacket.createFlvPacket(h264Buffer, info) { flvPacket ->
        val result = queue.trySend(flvPacket)
        if (!result) {
          Log.i(TAG, "Video frame discarded")
          droppedVideoFrames++
        }
      }
    }
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      audioPacket.createFlvPacket(aacBuffer, info) { flvPacket ->
        val result = queue.trySend(flvPacket)
        if (!result) {
          Log.i(TAG, "Audio frame discarded")
          droppedAudioFrames++
        }
      }
    }
  }

  fun start() {
    bitrateManager.reset()
    queue.clear()
    running = true
    job = scope.launch {
      var bytesSend = 0L
      val bitrateTask = async {
        while (scope.isActive && running) {
          //bytes to bits
          bitrateManager.calculateBitrate(bytesSend * 8)
          bytesSend = 0
          delay(timeMillis = 1000)
        }
      }
      while (scope.isActive && running) {
        val error = runCatching {
          val flvPacket = runInterruptible {
            queue.poll(1, TimeUnit.SECONDS)
          }
          if (flvPacket == null) {
            Log.i(TAG, "Skipping iteration, frame null")
          } else {
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
            bytesSend += size
          }
        }.exceptionOrNull()
        if (error != null) {
          onMainThread {
            connectChecker.onConnectionFailed("Error send packet, " + error.message)
          }
          Log.e(TAG, "send error: ", error)
          return@launch
        }
      }
    }
  }

  suspend fun stop(clear: Boolean = true) {
    running = false
    audioPacket.reset(clear)
    videoPacket.reset(clear)
    resetSentAudioFrames()
    resetSentVideoFrames()
    resetDroppedAudioFrames()
    resetDroppedVideoFrames()
    job?.cancelAndJoin()
    job = null
    queue.clear()
  }

  @Throws(IllegalArgumentException::class)
  fun hasCongestion(percentUsed: Float = 20f): Boolean {
    if (percentUsed < 0 || percentUsed > 100) throw IllegalArgumentException("the value must be in range 0 to 100")
    val size = queue.size.toFloat()
    val remaining = queue.remainingCapacity().toFloat()
    val capacity = size + remaining
    return size >= capacity * (percentUsed / 100f)
  }

  fun resizeCache(newSize: Int) {
    if (newSize < queue.size - queue.remainingCapacity()) {
      throw RuntimeException("Can't fit current cache inside new cache size")
    }
    val tempQueue: BlockingQueue<FlvPacket> = LinkedBlockingQueue(newSize)
    queue.drainTo(tempQueue)
    queue = tempQueue
  }

  fun getCacheSize(): Int {
    return cacheSize
  }

  fun getItemsInCache(): Int = queue.size

  fun clearCache() {
    queue.clear()
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

  fun setBitrateExponentialFactor(factor: Float) {
    bitrateManager.exponentialFactor = factor
  }

  fun getBitrateExponentialFactor() = bitrateManager.exponentialFactor
}