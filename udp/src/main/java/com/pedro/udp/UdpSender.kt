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

package com.pedro.udp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateManager
import com.pedro.common.ConnectChecker
import com.pedro.common.onMainThread
import com.pedro.common.trySend
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.Pid
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.packets.BasePacket
import com.pedro.srt.mpeg2ts.packets.H26XPacket
import com.pedro.srt.mpeg2ts.packets.OpusPacket
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.toCodec
import com.pedro.udp.utils.UdpSocket
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
 * Created by pedro on 6/3/24.
 */
class UdpSender(
  private val connectChecker: ConnectChecker,
  private val commandManager: CommandManager
) {

  private val service = Mpeg2TsService()

  private val psiManager = PsiManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }
  private val limitSize = Constants.MTU
  private val mpegTsPacketizer = MpegTsPacketizer(psiManager)
  private var audioPacket: BasePacket = AacPacket(limitSize, psiManager)
  private val h26XPacket = H26XPacket(limitSize, psiManager)

  @Volatile
  private var running = false
  private var cacheSize = 200

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  @Volatile
  private var queue: BlockingQueue<List<MpegTsPacket>> = LinkedBlockingQueue(cacheSize)
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: UdpSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set

  private val bitrateManager: BitrateManager = BitrateManager(connectChecker)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "SrtSender"
  }

  private fun setTrackConfig(videoEnabled: Boolean, audioEnabled: Boolean) {
    Pid.reset()
    service.clearTracks()
    if (audioEnabled) service.addTrack(commandManager.audioCodec.toCodec())
    if (videoEnabled) service.addTrack(commandManager.videoCodec.toCodec())
    service.generatePmt()
    psiManager.updateService(service)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    h26XPacket.setVideoCodec(commandManager.videoCodec.toCodec())
    h26XPacket.sendVideoInfo(sps, pps, vps)
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    when (commandManager.audioCodec) {
      AudioCodec.AAC -> {
        audioPacket = AacPacket(limitSize, psiManager)
        (audioPacket as? AacPacket)?.sendAudioInfo(sampleRate, isStereo)
      }
      AudioCodec.OPUS -> {
        audioPacket = OpusPacket(limitSize, psiManager)
      }
      AudioCodec.G711 -> {
        throw IllegalArgumentException("Unsupported codec: ${commandManager.audioCodec.name}")
      }
    }
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      h26XPacket.createAndSendPacket(h264Buffer, info) { mpegTsPackets ->
        val result = queue.trySend(mpegTsPackets)
        if (!result) {
          Log.i(TAG, "Video frame discarded")
          droppedVideoFrames++
        }
      }
    }
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      audioPacket.createAndSendPacket(aacBuffer, info) { mpegTsPackets ->
        val result = queue.trySend(mpegTsPackets)
        if (!result) {
          Log.i(TAG, "Audio frame discarded")
          droppedAudioFrames++
        }
      }
    }
  }

  fun start() {
    queue.clear()
    setTrackConfig(!commandManager.videoDisabled, !commandManager.audioDisabled)
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
          val mpegTsPackets = runInterruptible {
            queue.poll(1, TimeUnit.SECONDS)
          }
          val isKey = mpegTsPackets[0].isKey
          val psiPackets = psiManager.checkSendInfo(isKey, mpegTsPacketizer)
          bytesSend += sendPackets(psiPackets)
          bytesSend += sendPackets(mpegTsPackets)
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

  private suspend fun sendPackets(packets: List<MpegTsPacket>): Long {
    var bytesSend = 0L
    packets.forEach { mpegTsPacket ->
      var size = 0
      size += commandManager.writeData(mpegTsPacket, socket)
      if (isEnableLogs) {
        Log.i(TAG, "wrote ${mpegTsPacket.type.name} packet, size $size")
      }
      bytesSend += size
    }
    return bytesSend
  }

  suspend fun stop(clear: Boolean) {
    running = false
    psiManager.reset()
    service.clear()
    mpegTsPacketizer.reset()
    audioPacket.reset(clear)
    h26XPacket.reset(clear)
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
    val tempQueue: BlockingQueue<List<MpegTsPacket>> = LinkedBlockingQueue(newSize)
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
}