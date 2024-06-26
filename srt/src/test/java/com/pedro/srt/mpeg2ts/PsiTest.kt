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

import com.pedro.common.TimeUtils
import com.pedro.srt.Utils
import com.pedro.srt.mpeg2ts.psi.Pat
import com.pedro.srt.mpeg2ts.psi.Pmt
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.psi.Sdt
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.nio.ByteBuffer

/**
 * Created by pedro on 30/8/23.
 */
class PsiTest {

  private val service = Mpeg2TsService()
  private val timeUtilsMock = Mockito.mockStatic(TimeUtils::class.java)
  private val pidMock = Mockito.mockStatic(Pid::class.java)

  @Before
  fun setup() {
    timeUtilsMock.`when`<Long>(TimeUtils::getCurrentTimeMicro).thenReturn(700000)
    pidMock.`when`<Short>(Pid::generatePID).thenReturn(Pid.MIN_VALUE.toShort())
  }

  @After
  fun teardown() {
    service.clear()
  }

  @Test
  fun `GIVEN a sdt table WHEN create mpegts packet with that table THEN get expected buffer`() = runTest {
    Utils.useStatics(listOf(timeUtilsMock, pidMock)) {
      val expected = ByteBuffer.wrap(
        byteArrayOf(71, 64, 17, 16, 0, 66, -16, 49, 0, 1, -63, 0, 0, -1, 1, -1, 70, -104, -4, -128, 32, 72, 30, 1, 13, 99, 111, 109, 46, 112, 101, 100, 114, 111, 46, 115, 114, 116, 14, 77, 112, 101, 103, 50, 84, 115, 83, 101, 114, 118, 105, 99, 101, 72, 33, 81, -10, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
      )
      val psiManager = PsiManager(service)
      val mpegTsPacketizer = MpegTsPacketizer(psiManager)
      val sdt = Sdt(1, 0, service = service)
      val mpeg2tsPackets = mpegTsPacketizer.write(listOf(sdt))
      val chunked = mpeg2tsPackets
      val size = chunked.sumOf { it.size }
      val buffer = ByteBuffer.allocate(size)
      chunked.forEach {
        buffer.put(it)
      }
      assertArrayEquals(expected.array(), buffer.array())
    }
  }

  @Test
  fun `GIVEN a pmt table WHEN create mpegts packet with that table THEN get expected buffer`() = runTest {
    Utils.useStatics(listOf(timeUtilsMock, pidMock)) {
      service.addTrack(Codec.AAC)
      val expected = ByteBuffer.wrap(
        byteArrayOf(71, 64, 32, 16, 0, 2, -80, 18, 70, -104, -63, 0, 0, -32, 32, -16, 0, 15, -32, 32, -16, 0, 121, -48, -32, -74, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
      )
      val psiManager = PsiManager(service)
      val mpegTsPacketizer = MpegTsPacketizer(psiManager)
      val pmt = Pmt(32, 0, service = service)
      val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pmt))
      val chunked = mpeg2tsPackets
      val size = chunked.sumOf { it.size }
      val buffer = ByteBuffer.allocate(size)
      chunked.forEach {
        buffer.put(it)
      }
      assertArrayEquals(expected.array(), buffer.array())
    }
  }

  @Test
  fun `GIVEN a pat table WHEN create mpegts packet with that table THEN get expected buffer`() = runTest {
    Utils.useStatics(listOf(timeUtilsMock, pidMock)) {
      val expected = ByteBuffer.wrap(
        byteArrayOf(71, 64, 0, 16, 0, 0, -80, 13, 1, 0, -61, 0, 0, 70, -104, -32, 0, -30, -46, -114, -23, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
      )
      val psiManager = PsiManager(service)
      val mpegTsPacketizer = MpegTsPacketizer(psiManager)
      val pat = Pat(256, 1, service = service)
      val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pat))
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