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
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pid
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.packets.H26XPacket
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.psi.TableToSend
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.BitrateManager
import com.pedro.srt.utils.ConnectCheckerSrt
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.onMainThread
import com.pedro.srt.utils.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by pedro on 20/8/23.
 */
class SrtSender(
  private val connectCheckerSrt: ConnectCheckerSrt,
  private val commandsManager: CommandsManager
) {

  private val service = Mpeg2TsService()

  private var psiManager = PsiManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }

  private val mpegTsPacketizer = MpegTsPacketizer()
  private val aacPacket = AacPacket(commandsManager.MTU - SrtPacket.headerSize, psiManager)
  private val h26XPacket = H26XPacket(commandsManager.MTU - SrtPacket.headerSize, psiManager)

  @Volatile
  private var running = false
  private var cacheSize = 200

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  @Volatile
  private var queue: BlockingQueue<List<MpegTsPacket>> = LinkedBlockingQueue(cacheSize)
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: SrtSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
  var videoCodec = Codec.AVC
    set(value) {
      val videoTrack = service.tracks.find { it.codec != Codec.AAC }
      videoTrack?.let {
        service.tracks.remove(it)
      }
      service.addTrack(value)
      h26XPacket.setVideoCodec(value)
      field = value
    }

  private val bitrateManager: BitrateManager = BitrateManager(connectCheckerSrt)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "SrtSender"
  }

  private fun setTrackConfig(videoEnabled: Boolean, audioEnabled: Boolean) {
    Pid.reset()
    if (videoEnabled) service.addTrack(videoCodec)
    if (audioEnabled) service.addTrack(Codec.AAC)
    psiManager.updateService(service)
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    h26XPacket.sendVideoInfo(sps, pps, vps)
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      checkSendInfo()
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
      checkSendInfo()
      aacPacket.createAndSendPacket(aacBuffer, info) { mpegTsPackets ->
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
    setTrackConfig(!commandsManager.videoDisabled, !commandsManager.audioDisabled)
    running = true
    job = scope.launch {
      //send config
      val psiPackets = mpegTsPacketizer.write(listOf(psiManager.getPmt(), psiManager.getSdt(), psiManager.getPat())).map { b ->
        MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE)
      }
      queue.trySend(psiPackets)
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
          mpegTsPackets.forEach { mpegTsPacket ->
            var size = 0
            size += commandsManager.writeData(mpegTsPacket, socket)
            if (isEnableLogs) {
              Log.i(TAG, "wrote ${mpegTsPacket.type.name} packet, size $size")
            }
            bytesSend += size
          }
        }.exceptionOrNull()
        if (error != null) {
          onMainThread {
            connectCheckerSrt.onConnectionFailedSrt("Error send packet, " + error.message)
          }
          Log.e(TAG, "send error: ", error)
          return@launch
        }
      }
    }
  }

  private fun checkSendInfo() {
    when (psiManager.shouldSend(false)) {
      TableToSend.PAT_PMT -> {
        val psiPackets = mpegTsPacketizer.write(listOf(psiManager.getPmt(), psiManager.getPat())).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE)
        }
        queue.trySend(psiPackets)
      }
      TableToSend.SDT -> {
        val psiPackets = mpegTsPacketizer.write(listOf(psiManager.getSdt())).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE)
        }
        queue.trySend(psiPackets)
      }
      TableToSend.NONE -> {}
      TableToSend.ALL -> {
        val psiPackets = mpegTsPacketizer.write(listOf(psiManager.getPmt(), psiManager.getSdt(), psiManager.getPat())).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE)
        }
        queue.trySend(psiPackets)
      }
    }
  }

  suspend fun stop() {
    running = false
    psiManager.reset()
    service.clear()
    mpegTsPacketizer.reset()
    aacPacket.reset()
    h26XPacket.reset()
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