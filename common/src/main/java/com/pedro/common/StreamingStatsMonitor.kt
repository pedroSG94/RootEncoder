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

/**
 * Collects send-queue and throughput statistics each second and classifies bandwidth trends
 * over a sliding window, mirroring HaishinKit NetworkMonitor behavior.
 */
class StreamingStatsMonitor(private val bitrateChecker: BitrateChecker) {

  private val measureInterval = 3
  private val congestionThreshold = 95f
  private val previousQueueBytesOut: MutableList<Long> = mutableListOf()

  fun reset() {
    previousQueueBytesOut.clear()
  }

  suspend fun collect(
    queueBytesOut: Long,
    bytesOutPerSecond: Long,
    totalBytesOut: Long,
    smoothedBitrate: Long,
    queueCongestionPercent: Float,
  ) {
    var throughput = Throughput.UNKNOWN
    previousQueueBytesOut.add(queueBytesOut)
    if (measureInterval <= previousQueueBytesOut.size) {
      var countQueuedBytesGrowing = 0
      for (i in 0 until previousQueueBytesOut.size - 1) {
        if (previousQueueBytesOut[i] < previousQueueBytesOut[i + 1]) {
          countQueuedBytesGrowing++
        }
      }
      if (countQueuedBytesGrowing == measureInterval - 1) {
        throughput = Throughput.INSUFFICIENT
      } else if (countQueuedBytesGrowing == 0) {
        throughput = Throughput.SUFFICIENT
      }
      previousQueueBytesOut.removeAt(0)
    }
    // This library caps the queue at a fixed capacity,
    // so a saturated queue stops growing and will return Insufficient
    if (queueCongestionPercent >= congestionThreshold) {
      throughput = Throughput.INSUFFICIENT
    }

    val report = StreamingStatsReport(
      bytesOutPerSecond = bytesOutPerSecond,
      queueBytesOut = queueBytesOut,
      totalBytesOut = totalBytesOut,
      queueCongestionPercent = queueCongestionPercent,
      throughput = throughput,
      bitrate = bytesOutPerSecond * 8,
      smoothedBitrate = smoothedBitrate,
    )
    onMainThread { bitrateChecker.onStreamingStats(report) }
  }
}
