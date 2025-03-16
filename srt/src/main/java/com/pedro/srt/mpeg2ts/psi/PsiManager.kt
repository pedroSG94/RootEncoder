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

package com.pedro.srt.mpeg2ts.psi

import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.MpegTsPayload
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.chunkPackets
import kotlin.random.Random

/**
 * Created by pedro on 26/8/23.
 */
class PsiManager(
  private var service: Mpeg2TsService
) {

  private val idExtension = Random.nextInt(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toShort()
  private var sdtCount = 0
  private var patCount = 0

  companion object {
    const val sdtPeriod = 200
    const val patPeriod = 40
  }

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

  fun checkSendInfo(isKey: Boolean = false, mpegTsPacketizer: MpegTsPacketizer, chunkSize: Int): List<MpegTsPacket> {
    val pmt = service.pmt ?: return arrayListOf()
    val psiPackets = mutableListOf<MpegTsPayload>()
    if (patCount >= patPeriod || isKey) {
      psiPackets.addAll(listOf(pat, pmt))
      patCount = 0
    }
    if (sdtCount >= sdtPeriod) {
      psiPackets.add(sdt)
      sdtCount = 0
    }
    sdtCount++
    patCount++
    return mpegTsPacketizer.write(psiPackets, increasePsiContinuity = true).chunkPackets(chunkSize).map { b ->
      MpegTsPacket(b, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
    }
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
    sdtCount = 0
    patCount = 0
  }

  fun updateService(service: Mpeg2TsService) {
    this.service = service
    sdt.service = service
    pat.service = service
  }
}