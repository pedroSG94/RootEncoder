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

package com.pedro.library.util

import com.pedro.common.StreamingStatsReport
import com.pedro.common.Throughput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * maxBitrate is 3200000 of video plus 64000 of audio, the configuration reported in issue #2177.
 */
class QueueAwareBitrateAdapterTest {

  private val maxBitrate = 3264000

  private fun report(smoothedBitrate: Long, queueBytesOut: Long, throughput: Throughput) =
    StreamingStatsReport(
      bytesOutPerSecond = 0,
      queueBytesOut = queueBytesOut,
      totalBytesOut = 0,
      queueCongestionPercent = 0f,
      throughput = throughput,
      bitrate = 0,
      smoothedBitrate = smoothedBitrate,
    )

  private fun healthy(bitrate: Int) = report(bitrate.toLong(), 0, Throughput.SUFFICIENT)

  @Test
  fun `GIVEN a healthy link WHEN adapt for a long time THEN keep the whole configured bitrate`() {
    //the encoder starts at maxBitrate, an adapter that never touches it is the correct result
    var configured = maxBitrate
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { configured = it }
    repeat(300) { adapter.onStreamingStats(healthy(maxBitrate)) }
    assertEquals(maxBitrate, configured)
  }

  @Test
  fun `GIVEN the queue piling up WHEN adapt THEN drop below the bitrate the link delivered`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    adapter.onStreamingStats(report(3000000, 500000, Throughput.INSUFFICIENT))
    assertEquals(2700000, last)
  }

  @Test
  fun `GIVEN no measurement yet WHEN the queue piles up THEN do not report a zero bitrate`() {
    val emitted = mutableListOf<Int>()
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { emitted.add(it) }
    //BitrateManager reports 0 until its first one second window closes
    adapter.onStreamingStats(report(0, 900000, Throughput.INSUFFICIENT))
    assertEquals(emptyList<Int>(), emitted)
  }

  @Test
  fun `GIVEN a single bad second WHEN it recovers THEN never go under the floor`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    adapter.onStreamingStats(report(50000, 900000, Throughput.INSUFFICIENT))
    assertEquals(maxBitrate / 10, last)
  }

  @Test
  fun `GIVEN a throttled session WHEN reset THEN go back to max and tell the encoder`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    adapter.onStreamingStats(report(1000000, 900000, Throughput.INSUFFICIENT))
    assertTrue(last < maxBitrate)

    adapter.reset()
    assertEquals(maxBitrate, last)
  }

  @Test
  fun `GIVEN a link slower than max WHEN adapt in a closed loop THEN stay under it almost always`() {
    val link = 3000000
    var configured = maxBitrate
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { configured = it }
    var secondsOverTheLink = 0
    repeat(600) {
      val congested = configured > link
      if (congested) secondsOverTheLink++
      //the link only carries what it carries, and the queue grows while we push more
      adapter.onStreamingStats(
        if (congested) report(link.toLong(), 500000, Throughput.INSUFFICIENT)
        else healthy(configured)
      )
    }
    //it re-tests the link once every CEILING_TTL, so it goes over briefly by design
    assertTrue("over the link $secondsOverTheLink seconds of 600", secondsOverTheLink < 60)
  }
}
