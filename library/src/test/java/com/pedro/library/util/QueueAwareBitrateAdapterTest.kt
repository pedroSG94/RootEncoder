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
  fun `GIVEN a single terrible second WHEN it is the only measurement THEN do not collapse`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //a second delivering 50 kbps used to define the link and drop the bitrate to the floor
    adapter.onStreamingStats(report(50000, 900000, Throughput.INSUFFICIENT))
    assertEquals(1468800, last)
    assertTrue("collapsed to $last", last > maxBitrate / 4)
  }

  @Test
  fun `GIVEN backlogged seconds WHEN one goes bad THEN average them instead of trusting the worst`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //seconds with frames waiting are real link measurements, they build the capacity window
    repeat(10) { adapter.onStreamingStats(report(3000000, 500000, Throughput.INSUFFICIENT)) }
    adapter.onStreamingStats(report(50000, 900000, Throughput.INSUFFICIENT))
    //the average absorbs the transient instead of letting it define the link
    assertTrue("collapsed to $last", last > 2000000)
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

  @Test
  fun `GIVEN a bursty link WHEN the queue drains in a burst THEN do not read the burst as capacity`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //a shaped link stalls and then drains the backlog at twice the rate. Taking the best second
    //would read 6 Mbps as capacity on a link that only carries 2.5
    val pattern = listOf(2500000L, 0L, 6000000L, 2500000L, 1000000L, 6000000L, 2500000L)
    repeat(3) {
      pattern.forEach { adapter.onStreamingStats(report(it, 500000, Throughput.INSUFFICIENT)) }
    }
    assertTrue("aimed at $last, above what the link carries", last < 2500000)
  }

  @Test
  fun `GIVEN an empty queue WHEN adapt THEN do not use it as a capacity measurement`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //ten healthy seconds at max, the link was never the limit so they say nothing about capacity
    repeat(10) { adapter.onStreamingStats(healthy(maxBitrate)) }
    //now the link backs up and only delivers 1 Mbps. Had the healthy seconds been used as
    //measurements the window would average near maxBitrate and barely reduce anything
    adapter.onStreamingStats(report(1000000, 500000, Throughput.INSUFFICIENT))
    assertEquals(1468800, last)
  }

  @Test
  fun `GIVEN a link that collapses WHEN it probes back up THEN never go under the floor`() {
    val emitted = mutableListOf<Int>()
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { emitted.add(it) }
    //a link that keeps failing drives the ceiling down; the probe branch used to cap the
    //target at the collapsed ceiling, ignoring the floor and reaching zero
    repeat(40) {
      adapter.onStreamingStats(report(20000, 900000, Throughput.INSUFFICIENT))
      repeat(5) { adapter.onStreamingStats(healthy(20000)) }
    }
    val lowest = emitted.min()
    assertTrue("emitted $lowest, under the floor", lowest >= maxBitrate / 10)
  }

  @Test
  fun `GIVEN a link that stalls WHEN frames are waiting THEN count the stall as a measurement`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //one real value first, so the adapter knows BitrateManager is producing measurements
    adapter.onStreamingStats(report(2000000, 500000, Throughput.INSUFFICIENT))
    val afterSlowLink = last
    //now the link stops delivering entirely while frames pile up
    repeat(10) { adapter.onStreamingStats(report(0, 900000, Throughput.INSUFFICIENT)) }
    assertTrue("stalls did not lower the estimate: $afterSlowLink -> $last", last < afterSlowLink)
  }

  @Test
  fun `GIVEN no measurement yet WHEN the queue piles up THEN ignore the zero as a capacity value`() {
    var last = 0
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { last = it }
    //BitrateManager reports 0 until its first window closes; with a queue already growing that
    //zero must not be read as "the link delivers nothing"
    adapter.onStreamingStats(report(0, 900000, Throughput.INSUFFICIENT))
    assertEquals(0, last)
  }

  @Test
  fun `GIVEN a ttl of zero WHEN the link stays the same THEN never climb over it again`() {
    val link = 2800000
    var configured = maxBitrate
    val adapter = QueueAwareBitrateAdapter(maxBitrate, maxBitrate / 10, 0) { configured = it }
    var secondsOverTheLink = 0
    repeat(1800) {
      val over = configured > link
      if (over) secondsOverTheLink++
      adapter.onStreamingStats(
        if (over) report(link.toLong(), 500000, Throughput.INSUFFICIENT)
        else healthy(configured)
      )
    }
    //without re-testing it settles under the link and stays there
    assertTrue("over the link $secondsOverTheLink seconds of 1800", secondsOverTheLink < 30)
    assertTrue("settled at $configured, over the link", configured <= link)
  }

  @Test
  fun `GIVEN the default ttl WHEN the link stays the same THEN re-test it now and then`() {
    val link = 2800000
    var configured = maxBitrate
    val adapter = QueueAwareBitrateAdapter(maxBitrate) { configured = it }
    var overshoots = 0
    var wasOver = false
    repeat(1800) {
      val over = configured > link
      if (over && !wasOver) overshoots++
      wasOver = over
      adapter.onStreamingStats(
        if (over) report(link.toLong(), 500000, Throughput.INSUFFICIENT)
        else healthy(configured)
      )
    }
    //1800 seconds at the default ttl of 300 leaves a handful of re-tests, not one per minute
    assertTrue("re-tested $overshoots times in 1800s", overshoots in 1..8)
  }
}
