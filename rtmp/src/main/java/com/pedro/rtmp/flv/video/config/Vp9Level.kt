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

package com.pedro.rtmp.flv.video.config

import kotlin.math.max

/**
 * VP9 levels defined in VP9 Bitstream & Decoding Process Specification, Annex A.
 *
 * Levels 41, 51, 52, 61 and 62 are omitted because they only differ from 40, 50 and 60 in
 * max luma sample rate and max bitrate, values not available in the bitstream header.
 */
enum class Vp9Level(val value: Int, val maxLumaPictureSize: Int, val maxDimension: Int) {
  LEVEL_1(10, 36864, 512),
  LEVEL_1_1(11, 73728, 768),
  LEVEL_2(20, 122880, 960),
  LEVEL_2_1(21, 245760, 1344),
  LEVEL_3(30, 552960, 2048),
  LEVEL_3_1(31, 983040, 2752),
  LEVEL_4(40, 2228224, 4160),
  LEVEL_5(50, 8912896, 8384),
  LEVEL_6(60, 35651584, 16832);

  companion object {
    fun getLevel(width: Int, height: Int): Vp9Level {
      val pictureSize = width * height
      val dimension = max(width, height)
      return entries.firstOrNull {
        pictureSize <= it.maxLumaPictureSize && dimension <= it.maxDimension
      } ?: LEVEL_6
    }
  }
}
