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

package com.pedro.srt.mpeg2ts.psi

import com.pedro.common.TimeUtils
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.data.PacketPosition
import kotlin.random.Random

/**
 * Created by pedro on 26/8/23.
 */
class PsiManager(
  private var service: Mpeg2TsService
) {
  companion object {
    const val interval = 200 //ms
    const val sdtPeriod = 200
    const val patPeriod = 40
  }

  private val idExtension = Random.nextInt(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toShort()
  private var lastTime = 0L
  private var sdtCount = sdtPeriod
  private var patCount = patPeriod

  private var sdt = Sdt(
    idExtension = idExtension,
    version = 0,
    service = service
  )

  private var pat = Pat(
    idExtension = idExtension,
    version = 0,
    service = service
  )

  fun checkSendInfo(isKey: Boolean = false, mpegTsPacketizer: MpegTsPacketizer): List<MpegTsPacket> {
    val pmt = getPmt() ?: return arrayListOf()
    return when (shouldSend(isKey)) {
      TableToSend.PAT_PMT -> {
        val psiPackets = mpegTsPacketizer.write(listOf(getPat(), pmt), increasePsiContinuity = true).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
        }
        psiPackets
      }
      TableToSend.SDT -> {
        val psiPackets = mpegTsPacketizer.write(listOf(getSdt()), increasePsiContinuity = true).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
        }
        psiPackets
      }
      TableToSend.NONE -> arrayListOf()
      TableToSend.ALL -> {
        val psiPackets = mpegTsPacketizer.write(listOf(getPat(), pmt, getSdt()), increasePsiContinuity = true).map { b ->
          MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
        }
        psiPackets
      }
    }
  }

//  fun checkSendInfo(isKey: Boolean = false, mpegTsPacketizer: MpegTsPacketizer): List<MpegTsPacket> {
//    val pmt = getPmt() ?: return arrayListOf()
//    val currentTime = TimeUtils.getCurrentTimeMillis()
//    if (isKey || TimeUtils.getCurrentTimeMillis() - lastTime >= interval) {
//      lastTime = currentTime
//      val psiPackets = mpegTsPacketizer.write(listOf(getPat(), pmt, getSdt()), increasePsiContinuity = true).map { b ->
//        MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
//      }
//      return psiPackets
//    }
//    return arrayListOf()
//  }

  private fun shouldSend(isKey: Boolean = false): TableToSend {
    var value = TableToSend.NONE
    if (sdtCount >= sdtPeriod && patCount >= patPeriod) {
      value = TableToSend.ALL
      sdtCount = 0
      patCount = 0
    } else if (patCount >= patPeriod || isKey) {
      value = TableToSend.PAT_PMT
      patCount = 0
    } else if (sdtCount >= sdtPeriod) {
      value = TableToSend.SDT
      sdtCount = 0
    }
    sdtCount++
    patCount++
    return value
  }

  fun upgradeSdtVersion() {
    sdt.version = (sdt.version + 1.toByte()).toByte()
  }

  fun upgradePatVersion() {
    pat.version = (pat.version + 1.toByte()).toByte()
  }

  fun getAudioPid(): Short {
    return service.tracks.find { it.codec.isAudio() }?.pid ?: 0
  }

  fun getVideoPid(): Short {
    return service.tracks.find { !it.codec.isAudio() }?.pid ?: 0
  }

  fun getSdt(): Sdt = sdt
  fun getPat(): Pat = pat
  fun getPmt(): Pmt? = service.pmt

  fun reset() {
    lastTime = 0
    sdtCount = 0
    patCount = 0
  }

  fun updateService(service: Mpeg2TsService) {
    this.service = service
    sdt.service = service
    pat.service = service
  }
}