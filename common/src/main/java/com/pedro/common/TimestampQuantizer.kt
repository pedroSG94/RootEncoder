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
 * A source faster than the configured fps (a 60fps video file streamed at 30fps) is snapped
 * to half the grid instead. That case is detected checking that the frames really land on the
 * half grid, the average interval isn't enough because those sources skip slots in a
 * irregular way.
 *
 * If the source isn't running at a rate that fits any of both grids the timestamp is returned
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
  private var gridError = 0.0
  private var halfGridError = 0.0
  private var gridInterval = 0.0
  private var frames = 0
  private var halfVotes = 0

  fun reset() {
    lastTimestamp = NO_VALUE
    lastResult = NO_VALUE
    averageInterval = 0.0
    gridError = 0.0
    halfGridError = 0.0
    gridInterval = 0.0
    frames = 0
    halfVotes = 0
  }

  fun quantize(timestamp: Long, fps: Int): Long {
    if (fps <= 0) return timestamp
    val nominal = 1_000_000.0 / fps
    if (lastTimestamp == NO_VALUE) return anchor(timestamp, timestamp, nominal)
    val rawInterval = (timestamp - lastTimestamp).toDouble()
    lastTimestamp = timestamp
    frames++
    averageInterval =
        if (averageInterval == 0.0) rawInterval
        else averageInterval + (rawInterval - averageInterval) / SMOOTH_FACTOR
    val half = nominal / 2
    gridError = fitError(gridError, rawInterval / nominal)
    halfGridError = fitError(halfGridError, rawInterval / half)

    //faster than the grid and landing on the half grid (60fps file in a 30fps stream).
    //The half grid has denser slots so it can fit a jittery source by chance: only use it if
    //it fits clearly better than the nominal one and it does it in a sustained way
    val fitsHalf = frames > SMOOTH_FACTOR && averageInterval >= half * MIN_FILL
        && halfGridError <= TOLERANCE && halfGridError < gridError * BETTER_FIT
    halfVotes = if (fitsHalf) halfVotes + 1 else 0

    val ratio = averageInterval / nominal
    val slotsPerFrame = ratio.roundToLong()
    val interval = when {
      //a frame per slot or skipping slots in a regular way (15fps in a 30fps grid)
      slotsPerFrame >= 1 && abs(ratio - slotsPerFrame) <= TOLERANCE -> nominal
      halfVotes >= SMOOTH_FACTOR -> half
      //the rate doesn't fit any grid, using a grid would be a lie
      else -> return anchor(timestamp, max(lastResult + 1, timestamp), nominal)
    }
    if (interval != gridInterval) return anchor(timestamp, max(lastResult + 1, timestamp), interval)
    var slot = ((timestamp - anchorTimestamp) / interval).roundToLong()
    if (slot <= lastSlot) slot = lastSlot + 1
    val result = anchorResult + (slot * interval).toLong()
    //too far from the clock (frames faster than the grid), sync again to avoid desync
    if (abs(result - timestamp) > interval) {
      return anchor(timestamp, max(lastResult + interval.toLong(), timestamp), interval)
    }
    lastSlot = slot
    lastResult = result
    return result
  }

  //how far the interval is from landing on a grid slot, 0 = exactly on a slot
  private fun fitError(current: Double, slots: Double): Double {
    val error = abs(slots - slots.roundToLong())
    return if (current == 0.0) error else current + (error - current) / SMOOTH_FACTOR
  }

  private fun anchor(timestamp: Long, result: Long, interval: Double): Long {
    lastTimestamp = timestamp
    anchorTimestamp = timestamp
    anchorResult = result
    lastSlot = 0
    lastResult = result
    gridInterval = interval
    return result
  }

  companion object {
    private const val NO_VALUE = Long.MIN_VALUE
    //number of frames used to calculate the real interval of the source
    private const val SMOOTH_FACTOR = 8
    //max difference allowed between the real interval and the expected one
    private const val TOLERANCE = 0.2
    //the half grid can't be used if the source is slower than it
    private const val MIN_FILL = 0.8
    //how much better the half grid must fit to be used instead of the nominal one
    private const val BETTER_FIT = 0.75
  }
}
