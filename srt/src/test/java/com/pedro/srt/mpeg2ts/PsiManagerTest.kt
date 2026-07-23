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

package com.pedro.srt.mpeg2ts

import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 9/9/23.
 */
class PsiManagerTest {

  private val service = Mpeg2TsService().apply { generatePmt() }
  private val psiManager = PsiManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }
  private val mpegTsPacketizer = MpegTsPacketizer(psiManager)
  private val chunkSize = 1

  @Test
  fun `GIVEN a psiManager WHEN call should send is key false patPeriod times THEN return PAT and PMT packets`() {
    val psiManager = PsiManager(service)
    var packets = listOf<MpegTsPacket>()
    (0..PsiManager.patPeriod).forEach { _ ->
      packets = psiManager.checkSendInfo(false, mpegTsPacketizer, chunkSize)
    }
    assertEquals(2, packets.size)
  }

  @Test
  fun `GIVEN a psiManager WHEN call should send is key false sdtPeriod times THEN return PMT, SDT and PAT packets`() {
    val psiManager = PsiManager(service)
    var packets = listOf<MpegTsPacket>()
    (0..PsiManager.sdtPeriod).forEach { _ ->
      packets = psiManager.checkSendInfo(false, mpegTsPacketizer, chunkSize)
    }
    assertEquals(3, packets.size)
  }

  @Test
  fun `GIVEN a psiManager WHEN update pat and sdt version THEN get version 1`() {
    val psiManager = PsiManager(service)
    psiManager.upgradePatVersion()
    psiManager.upgradeSdtVersion()
    assertEquals(0x01.toByte(), psiManager.getPat().version)
    assertEquals(0x01.toByte(), psiManager.getSdt().version)
  }

  @Test
  fun `GIVEN a psiManager WHEN update update service THEN get get new service in pat and sdt`() {
    val psiManager = PsiManager(service)
    val name = "name updated"
    val serviceUpdated = service.copy(name = name)
    psiManager.updateService(serviceUpdated)
    assertEquals(name, psiManager.getPat().service.name)
    assertEquals(name, psiManager.getSdt().service.name)
  }
}