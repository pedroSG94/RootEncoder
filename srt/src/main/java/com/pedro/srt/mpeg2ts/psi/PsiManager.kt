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

import com.pedro.srt.mpeg2ts.Codec
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import kotlin.random.Random

/**
 * Created by pedro on 26/8/23.
 */
class PsiManager(
  private val service: Mpeg2TsService
) {

  private val idExtension = Random.nextInt(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toShort()
  private var sdtCount = 0
  private var patCount = 0
  private val sdtPeriod = 200
  private val patPeriod = 40

  private val sdt = Sdt(
    idExtension = idExtension,
    version = 0,
    service = service
  )

  private val pat = Pat(
    idExtension = idExtension,
    version = 0,
    service = service
  )

  fun shouldSend(isKey: Boolean = false): TableToSend {
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
    return service.tracks.find { it.codec == Codec.AAC }?.pid ?: 0
  }

  fun getVideoPid(): Short {
    return service.tracks.find { it.codec != Codec.AAC }?.pid ?: 0
  }

  fun getSdt(): Sdt = sdt
  fun getPat(): Pat = pat
  fun getPmt(): Pmt = service.pmt!!

  fun reset() {
    sdtCount = 0
    patCount = 0
  }
}