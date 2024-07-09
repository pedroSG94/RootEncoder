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
 * Created by pedro on 8/04/21.
 * Improved by perotom on 7/05/24.
 *
 * Calculate video and audio bitrate per second based on an exponential moving average.
 */
open class BitrateManager(private val bitrateChecker: BitrateChecker) {

  private var bitrate = 0L
  private var bitrateOld = 0L
  var exponentialFactor: Float = 1f
  private var timeStamp = TimeUtils.getCurrentTimeMillis()

  suspend fun calculateBitrate(size: Long) {
    bitrate += size
    val timeDiff = TimeUtils.getCurrentTimeMillis() - timeStamp
    if (timeDiff >= 1000) {
      val currentValue = (bitrate / (timeDiff / 1000f)).toLong()
      if (bitrateOld == 0L) { bitrateOld = currentValue }
      bitrateOld = (bitrateOld + exponentialFactor * (currentValue - bitrateOld)).toLong()
      onMainThread { bitrateChecker.onNewBitrate(bitrateOld) }
      timeStamp = TimeUtils.getCurrentTimeMillis()
      bitrate = 0
    }
  }

  fun reset() {
    bitrate = 0
    bitrateOld = 0
  }
}