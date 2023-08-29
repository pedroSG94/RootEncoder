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
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.psi.PSIManager
import com.pedro.srt.mpeg2ts.psi.TableToSend
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
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

  private val service = Mpeg2TsService().apply {
    addTrack(Codec.AAC)
//    addTrack(Codec.AVC)
  }
  private val psiManager = PSIManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }

  private val mpegTsPacketizer = MpegTsPacketizer()
  private val aacPacket = AacPacket(commandsManager.MTU - SrtPacket.headerSize, psiManager)

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
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {

    }
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      checkSendInfo()
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
      //send config
      mpegTsPacketizer.write(listOf(psiManager.getSdt(), psiManager.getPat(), psiManager.getPmt())).forEach { b ->
        queue.trySend(MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE)).exceptionOrNull()
      }
      queueFlow.collect { mpegTsPacket ->
        itemsInQueue--
        val error = runCatching {
          var size = 0
          size += commandsManager.writeData(mpegTsPacket, socket)
          if (isEnableLogs) {
            Log.i(TAG, "wrote ${mpegTsPacket.type.name} packet, size $size")
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

  private fun checkSendInfo() {
    when (psiManager.shouldSend(false)) {
      TableToSend.PAT_PMT -> {
        mpegTsPacketizer.write(listOf(psiManager.getPat(), psiManager.getPmt())).forEach {
          queue.trySend(MpegTsPacket(it, MpegType.PSI, PacketPosition.SINGLE)).exceptionOrNull()
        }
      }
      TableToSend.SDT -> {
        mpegTsPacketizer.write(listOf(psiManager.getSdt())).forEach {
          queue.trySend(MpegTsPacket(it, MpegType.PSI, PacketPosition.SINGLE)).exceptionOrNull()
        }
      }
      TableToSend.NONE -> {}
      TableToSend.ALL -> {
        mpegTsPacketizer.write(listOf(psiManager.getSdt(), psiManager.getPat(), psiManager.getPmt())).forEach {
          queue.trySend(MpegTsPacket(it, MpegType.PSI, PacketPosition.SINGLE)).exceptionOrNull()
        }
      }
    }
  }

  suspend fun stop() {
    running = false
    queue.cancel()
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    psiManager.reset()
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