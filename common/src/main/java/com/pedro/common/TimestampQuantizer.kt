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

package com.pedro.common

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Created by pedro on 4/8/26.
 *
 * Timestamps generated from the clock carry the jitter of the thread that reads it
 * (in a render thread it can be +-15ms at 30fps). That jitter is harmless for a live
 * player but it makes the result a variable frame rate stream, so ffprobe/players can't
 * detect the frame rate of a recorded file and the playback looks jumpy.
 *
 * This snaps each timestamp to the closest slot of the expected frame rate grid, keeping
 * the values close to the original clock, so the frame rate is stable without desync.
 *
 * Slots can be skipped (a 15fps source in a 30fps grid is preserved) but the value is never
 * repeated or decreased.
 *
 * If the source isn't running at a rate that fits the grid the timestamp is returned
 * untouched. This is the case of a screen source without changes on screen or a video file
 * recorded in a different frame rate, where a grid would be a lie.
 *
 * All the values are in microseconds.
 */
class TimestampQuantizer {

  private var lastTimestamp = NO_VALUE
  private var lastResult = NO_VALUE
  private var anchorTimestamp = 0L
  private var anchorResult = 0L
  private var lastSlot = 0L
  private var averageInterval = 0.0

  fun reset() {
    lastTimestamp = NO_VALUE
    lastResult = NO_VALUE
    averageInterval = 0.0
  }

  fun quantize(timestamp: Long, fps: Int): Long {
    if (fps <= 0) return timestamp
    val interval = 1_000_000.0 / fps
    if (lastTimestamp == NO_VALUE) return anchor(timestamp, timestamp)
    val rawInterval = (timestamp - lastTimestamp).toDouble()
    lastTimestamp = timestamp
    averageInterval =
        if (averageInterval == 0.0) rawInterval
        else averageInterval + (rawInterval - averageInterval) / SMOOTH_FACTOR
    //the source can produce a frame per slot or skip slots (15fps in a 30fps grid) but a
    //rate that doesn't fit the grid (24fps in a 30fps grid) would be worse quantized
    val ratio = averageInterval / interval
    val slotsPerFrame = ratio.roundToLong()
    if (slotsPerFrame < 1 || abs(ratio - slotsPerFrame) > TOLERANCE) {
      //the source isn't running at the expected fps, use the clock as is
      return anchor(timestamp, max(lastResult + 1, timestamp))
    }
    var slot = ((timestamp - anchorTimestamp) / interval).roundToLong()
    if (slot <= lastSlot) slot = lastSlot + 1
    val result = anchorResult + (slot * interval).toLong()
    //too far from the clock (frames faster than the grid), sync again to avoid desync
    if (abs(result - timestamp) > interval) {
      return anchor(timestamp, max(lastResult + interval.toLong(), timestamp))
    }
    lastSlot = slot
    lastResult = result
    return result
  }

  private fun anchor(timestamp: Long, result: Long): Long {
    lastTimestamp = timestamp
    anchorTimestamp = timestamp
    anchorResult = result
    lastSlot = 0
    lastResult = result
    return result
  }

  companion object {
    private const val NO_VALUE = Long.MIN_VALUE
    //number of frames used to calculate the real interval of the source
    private const val SMOOTH_FACTOR = 8
    //max difference allowed between the real interval and the expected one
    private const val TOLERANCE = 0.2
  }
}
