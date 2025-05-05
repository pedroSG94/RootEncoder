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

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.base.BaseSender
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.validMessage
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pid
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.packets.BasePacket
import com.pedro.srt.mpeg2ts.packets.H26XPacket
import com.pedro.srt.mpeg2ts.packets.OpusPacket
import com.pedro.srt.mpeg2ts.psi.Psi
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.chunkPackets
import com.pedro.srt.utils.toCodec
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by pedro on 20/8/23.
 */
class SrtSender(
  connectChecker: ConnectChecker,
  private val commandsManager: CommandsManager
): BaseSender(connectChecker, "SrtSender") {

  private var service = Mpeg2TsService()
  private val psiManager = PsiManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }
  private val limitSize: Int
    get() {
      return commandsManager.MTU - SrtPacket.headerSize
    }

  private val mpegTsPacketizer = MpegTsPacketizer(psiManager)
  private var audioPacket: BasePacket = AacPacket(limitSize, psiManager)
  private val videoPacket = H26XPacket(limitSize, psiManager)
  var socket: SrtSocket? = null

  private fun setTrackConfig(videoEnabled: Boolean, audioEnabled: Boolean) {
    Pid.reset()
    service.clearTracks()
    if (audioEnabled) service.addTrack(commandsManager.audioCodec.toCodec())
    if (videoEnabled) service.addTrack(commandsManager.videoCodec.toCodec())
    service.generatePmt()
    psiManager.updateService(service)
  }

  override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    videoPacket.setVideoCodec(commandsManager.videoCodec.toCodec())
    videoPacket.sendVideoInfo(sps, pps, vps)
  }

  override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    audioPacket = when (commandsManager.audioCodec) {
      AudioCodec.AAC -> AacPacket(limitSize, psiManager).apply { sendAudioInfo(sampleRate, isStereo) }
      AudioCodec.OPUS -> OpusPacket(limitSize, psiManager)
      AudioCodec.G711 -> {
        throw IllegalArgumentException("Unsupported codec: ${commandsManager.audioCodec.name}")
      }
    }
  }

  /**
   * Set a custom Mpeg2TsService to use for the stream
   * 
   * @param customService the custom Mpeg2TsService to use
   */
  fun setMpeg2TsService(customService: Mpeg2TsService) {
    if (!running) {
      service = customService
      psiManager.updateService(service)
      psiManager.upgradePatVersion()
      psiManager.upgradeSdtVersion()
    }
  }

  override suspend fun onRun() {
    val limitSize = this.limitSize
    val chunkSize = limitSize / MpegTsPacketizer.packetSize
    audioPacket.setLimitSize(limitSize)
    videoPacket.setLimitSize(limitSize)

    setTrackConfig(!commandsManager.videoDisabled, !commandsManager.audioDisabled)
    //send config
    val psiList = mutableListOf<Psi>(psiManager.getPat())
    psiManager.getPmt()?.let { psiList.add(0, it) }
    psiList.add(psiManager.getSdt())
    val psiPacketsConfig = mpegTsPacketizer.write(psiList).chunkPackets(chunkSize).map { buffer ->
      MpegTsPacket(buffer, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
    }
    sendPackets(psiPacketsConfig, MpegType.PSI)
    while (scope.isActive && running) {
      val error = runCatching {
        val mediaFrame = runInterruptible { queue.take() }
        getMpegTsPackets(mediaFrame) { mpegTsPackets ->
          val isKey = mpegTsPackets[0].isKey
          val psiPackets = psiManager.checkSendInfo(isKey, mpegTsPacketizer, chunkSize)
          bytesSend += sendPackets(psiPackets, MpegType.PSI)
          bytesSend += sendPackets(mpegTsPackets, mpegTsPackets[0].type)
        }
      }.exceptionOrNull()
      if (error != null) {
        onMainThread {
          connectChecker.onConnectionFailed("Error send packet, ${error.validMessage()}")
        }
        Log.e(TAG, "send error: ", error)
        running = false
        return
      }
    }
  }

  override suspend fun stopImp(clear: Boolean) {
    psiManager.reset()
    if (clear) service = Mpeg2TsService()
    mpegTsPacketizer.reset()
    audioPacket.reset(clear)
    videoPacket.reset(clear)
  }

  private suspend fun sendPackets(packets: List<MpegTsPacket>, type: MpegType): Long {
    if (packets.isEmpty()) return 0
    var bytesSend = 0L
    packets.forEach { mpegTsPacket ->
      var size = 0
      size += commandsManager.writeData(mpegTsPacket, socket)
      bytesSend += size
    }
    if (type == MpegType.VIDEO) videoFramesSent++
    else if (type == MpegType.AUDIO) audioFramesSent++
    if (isEnableLogs) {
      Log.i(TAG, "wrote ${type.name} packet, size $bytesSend")
    }
    return bytesSend
  }

  private suspend fun getMpegTsPackets(mediaFrame: MediaFrame?, callback: suspend (List<MpegTsPacket>) -> Unit) {
    if (mediaFrame == null) return
    when (mediaFrame.type) {
      MediaFrame.Type.VIDEO -> videoPacket.createAndSendPacket(mediaFrame) { callback(it) }
      MediaFrame.Type.AUDIO -> audioPacket.createAndSendPacket(mediaFrame) { callback(it) }
    }
  }
}