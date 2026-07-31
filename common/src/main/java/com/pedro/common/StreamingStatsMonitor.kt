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
  private val previousQueueBytesOut: MutableList<Long> = mutableListOf()
  private var previousTotalBytesIn = 0L

  fun reset() {
    previousQueueBytesOut.clear()
    previousTotalBytesIn = 0L
  }

  suspend fun collect(
    queueBytesOut: Long,
    bytesOutPerSecond: Long,
    totalBytesOut: Long,
    totalBytesIn: Long,
    smoothedBitrate: Long,
  ) {
    val bytesInPerSecond = totalBytesIn - previousTotalBytesIn
    previousTotalBytesIn = totalBytesIn

    var throughput = Throughput.Unknown
    previousQueueBytesOut.add(queueBytesOut)
    if (measureInterval <= previousQueueBytesOut.size) {
      var countQueuedBytesGrowing = 0
      for (i in 0 until previousQueueBytesOut.size - 1) {
        if (previousQueueBytesOut[i] < previousQueueBytesOut[i + 1]) {
          countQueuedBytesGrowing++
        }
      }
      if (countQueuedBytesGrowing == measureInterval - 1) {
        throughput = Throughput.Insufficient
      } else if (countQueuedBytesGrowing == 0) {
        throughput = Throughput.Sufficient
      }
      previousQueueBytesOut.removeAt(0)
    }

    val report = StreamingStatsReport(
      bytesOutPerSecond = bytesOutPerSecond,
      queueBytesOut = queueBytesOut,
      totalBytesOut = totalBytesOut,
      totalBytesIn = totalBytesIn,
      bytesInPerSecond = bytesInPerSecond,
      throughput = throughput,
      bitrate = bytesOutPerSecond * 8,
      smoothedBitrate = smoothedBitrate,
    )
    onMainThread { bitrateChecker.onStreamingStats(report) }
  }
}
