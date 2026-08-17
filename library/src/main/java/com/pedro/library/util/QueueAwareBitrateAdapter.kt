/*
 * Copyright (C) 2026 pedroSG94.
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

/**
 * Alternative to [BitrateAdapter] driven by the send queue instead of by the measured bitrate.
 *
 * [BitrateAdapter] probes upwards blindly and only reduces when the measured bitrate falls far
 * enough below the configured one, so a link slightly slower than the target makes it oscillate
 * above and below the real capacity. This one reads the queue: when frames start piling up the
 * link is the limit, so it records the bitrate the link actually delivered and stays below it
 * instead of climbing back to the maximum.
 *
 * Feed it from onStreamingStats and apply [Listener.onBitrateAdapted] to the video encoder.
 * [maxBitrate] is the whole wire budget, video plus audio, because that is what the report
 * measures, so subtract the audio bitrate before applying it to video.
 */

class QueueAwareBitrateAdapter(
  private val maxBitrate: Int,
  minBitrate: Int,
  private val listener: Listener
) {

  constructor(maxBitrate: Int, listener: Listener): this(maxBitrate, maxBitrate / 10, listener)

  fun interface Listener {
    fun onBitrateAdapted(bitrate: Int)
  }

  private companion object {
    const val QUEUE_ALERT_FRACTION = 0.15f //seconds of video allowed in queue
    const val BACKOFF = 0.90f
    const val PROBE = 1.05f
    const val HOLD_SECONDS = 4
    const val CEILING_MARGIN = 0.97f
    const val CEILING_TTL = 60
    //a transient must not define the link, so the capacity is the best second of this window
    const val CAPACITY_WINDOW = 15
    //and even a real drop cannot halve the ceiling twice in a row
    const val MAX_CEILING_DROP = 0.5f
    //each probe closes part of the distance to the ceiling, so recovering from a deep drop
    //does not take minutes
    const val GAP_CLOSE = 0.25f
  }

  private val floor = minBitrate.coerceIn(1, maxBitrate)
  private val recent = ArrayDeque<Long>()
  private var target = maxBitrate
  private var ceiling = maxBitrate
  private var good = 0
  private var age = 0

  fun onStreamingStats(report: StreamingStatsReport) {
    //BitrateManager reports 0 until its first window closes, that is not a measurement
    if (report.smoothedBitrate > 0) {
      recent.addLast(report.smoothedBitrate)
      if (recent.size > CAPACITY_WINDOW) recent.removeFirst()
    }
    val alertBytes = (target / 8) * QUEUE_ALERT_FRACTION
    val congested = report.throughput == Throughput.INSUFFICIENT || report.queueBytesOut > alertBytes
    if (congested) {
      val measured = recent.maxOrNull()
      if (measured != null) {
        val dropped = minOf(ceiling.toLong(), measured).toInt()
        ceiling = maxOf(dropped, (ceiling * MAX_CEILING_DROP).toInt())
        target = (ceiling * BACKOFF).toInt().coerceAtLeast(floor)
        good = 0
        age = 0
        listener.onBitrateAdapted(target)
      }
    } else {
      good++
      if (good >= HOLD_SECONDS) {
        good = 0
        val cap = if (ceiling >= maxBitrate) maxBitrate else (ceiling * CEILING_MARGIN).toInt()
        val gapStep = target + ((cap - target) * GAP_CLOSE).toInt()
        val next = minOf(maxOf(gapStep, (target * PROBE).toInt()), cap)
        if (next != target) {
          target = next
          listener.onBitrateAdapted(target)
        }
      }
    }
    if (++age > CEILING_TTL) {
      ceiling = maxBitrate
      age = 0
    }
  }

  fun reset() {
    target = maxBitrate
    ceiling = maxBitrate
    good = 0
    age = 0
    recent.clear()
    listener.onBitrateAdapted(target)
  }
}
