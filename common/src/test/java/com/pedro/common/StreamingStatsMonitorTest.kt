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

package com.pedro.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(MockitoJUnitRunner::class)
class StreamingStatsMonitorTest {

  @OptIn(ExperimentalCoroutinesApi::class)
  @get:Rule
  val mainDispatcherRule = object : TestWatcher() {
    override fun starting(description: Description) {
      Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
      Dispatchers.resetMain()
    }
  }

  @Mock
  private lateinit var connectChecker: ConnectChecker

  private lateinit var monitor: StreamingStatsMonitor

  @Before
  fun setup() {
    monitor = StreamingStatsMonitor(connectChecker)
  }

  @Test
  fun `WHEN fewer than 3 samples THEN throughput is Unknown`() = runTest {
    monitor.collect(
      queueBytesOut = 100L,
      bytesOutPerSecond = 1000L,
      totalBytesOut = 1000L,
      smoothedBitrate = 8000L,
      queueCongestionPercent = 0f,
    )
    monitor.collect(
      queueBytesOut = 200L,
      bytesOutPerSecond = 1000L,
      totalBytesOut = 2000L,
      smoothedBitrate = 8000L,
      queueCongestionPercent = 0f,
    )

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(2)).onStreamingStats(captor.capture())
    captor.allValues.forEach { assertEquals(Throughput.UNKNOWN, it.throughput) }
  }

  @Test
  fun `WHEN queue bytes grow for 3 intervals THEN throughput is Insufficient`() = runTest {
    repeat(2) {
      monitor.collect(
        queueBytesOut = (it + 1) * 100L,
        bytesOutPerSecond = 500L,
        totalBytesOut = 500L,
        smoothedBitrate = 4000L,
        queueCongestionPercent = 0f,
      )
    }
    monitor.collect(
      queueBytesOut = 300L,
      bytesOutPerSecond = 500L,
      totalBytesOut = 1500L,
      smoothedBitrate = 4000L,
      queueCongestionPercent = 0f,
    )

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(3)).onStreamingStats(captor.capture())
    assertEquals(Throughput.INSUFFICIENT, captor.lastValue.throughput)
    assertEquals(300L, captor.lastValue.queueBytesOut)
    assertEquals(500L, captor.lastValue.bytesOutPerSecond)
    assertEquals(4000L, captor.lastValue.bitrate)
  }

  @Test
  fun `WHEN queue bytes shrink for 3 intervals THEN throughput is Sufficient`() = runTest {
    monitor.collect(
      queueBytesOut = 300L,
      bytesOutPerSecond = 800L,
      totalBytesOut = 800L,
      smoothedBitrate = 6400L,
      queueCongestionPercent = 0f,
    )
    monitor.collect(
      queueBytesOut = 200L,
      bytesOutPerSecond = 800L,
      totalBytesOut = 1600L,
      smoothedBitrate = 6400L,
      queueCongestionPercent = 0f,
    )
    monitor.collect(
      queueBytesOut = 100L,
      bytesOutPerSecond = 800L,
      totalBytesOut = 2400L,
      smoothedBitrate = 6400L,
      queueCongestionPercent = 0f,
    )

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(3)).onStreamingStats(captor.capture())
    assertEquals(Throughput.SUFFICIENT, captor.lastValue.throughput)
  }

  @Test
  fun `WHEN queue trend is mixed THEN throughput stays Unknown`() = runTest {
    monitor.collect(queueBytesOut = 100L, bytesOutPerSecond = 100L, totalBytesOut = 100L, smoothedBitrate = 800L, queueCongestionPercent = 0f)
    monitor.collect(queueBytesOut = 200L, bytesOutPerSecond = 100L, totalBytesOut = 200L, smoothedBitrate = 800L, queueCongestionPercent = 0f)
    monitor.collect(queueBytesOut = 150L, bytesOutPerSecond = 100L, totalBytesOut = 300L, smoothedBitrate = 800L, queueCongestionPercent = 0f)

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(3)).onStreamingStats(captor.capture())
    assertEquals(Throughput.UNKNOWN, captor.lastValue.throughput)
  }

  @Test
  fun `WHEN reset THEN trend window clears`() = runTest {
    repeat(3) {
      monitor.collect(
        queueBytesOut = (it + 1) * 100L,
        bytesOutPerSecond = 100L,
        totalBytesOut = 100L,
        smoothedBitrate = 800L,
        queueCongestionPercent = 0f,
      )
    }
    monitor.reset()
    monitor.collect(
      queueBytesOut = 400L,
      bytesOutPerSecond = 100L,
      totalBytesOut = 100L,
      smoothedBitrate = 800L,
      queueCongestionPercent = 0f,
    )

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(4)).onStreamingStats(captor.capture())
    assertEquals(Throughput.UNKNOWN, captor.lastValue.throughput)
  }

  @Test
  fun `WHEN queue is congested THEN throughput is Insufficient even with a flat queue trend`() = runTest {
    //queueBytesOut stays flat because the queue is saturated at its fixed capacity, not
    //because throughput is healthy, so the trend alone would misread this as Sufficient
    repeat(3) {
      monitor.collect(
        queueBytesOut = 1000L,
        bytesOutPerSecond = 100L,
        totalBytesOut = 100L,
        smoothedBitrate = 800L,
        queueCongestionPercent = 100f,
      )
    }

    val captor = argumentCaptor<StreamingStatsReport>()
    verify(connectChecker, times(3)).onStreamingStats(captor.capture())
    assertEquals(Throughput.INSUFFICIENT, captor.lastValue.throughput)
    assertEquals(100f, captor.lastValue.queueCongestionPercent)
  }
}
