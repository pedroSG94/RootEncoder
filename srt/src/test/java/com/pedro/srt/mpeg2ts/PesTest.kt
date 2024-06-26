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

import com.pedro.srt.Utils
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.common.TimeUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.nio.ByteBuffer

/**
 * Created by pedro on 30/8/23.
 *
 */
class PesTest {

  private val service = Mpeg2TsService()
  private val timeUtilsMock = Mockito.mockStatic(TimeUtils::class.java)

  @Before
  fun setup() {
    timeUtilsMock.`when`<Long>(TimeUtils::getCurrentTimeMicro).thenReturn(700000)
  }

  @Test
  fun `GIVEN a fake aac buffer WHEN create a mpegts packet with pes packet THEN get the expected buffer`() = runTest {
    Utils.useStatics(listOf(timeUtilsMock)) {
      val data = ByteBuffer.wrap(
        ByteArray(188) { 0xAA.toByte() }
      )
      val expected = ByteBuffer.wrap(
        byteArrayOf(71, 65, 0, 48, 7, 80, 0, 0, 123, 12, 126, 0, 0, 0, 1, -64, 0, -60, -127, -128, 5, 33, 0, 7, -40, 97, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, 71, 1, 0, 49, -99, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86, -86)
      )
      val psiManager = PsiManager(service)
      val mpegTsPacketizer = MpegTsPacketizer(psiManager)
      val pes = Pes(256, true, PesType.AUDIO, 1400000, data)
      val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes))
      val chunked = mpeg2tsPackets
      val size = chunked.sumOf { it.size }
      val buffer = ByteBuffer.allocate(size)
      chunked.forEach {
        buffer.put(it)
      }
      assertArrayEquals(expected.array(), buffer.array())
    }
  }
}