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

/**
 * VP9 color spaces defined in VP9 Bitstream & Decoding Process Specification, section 6.2.2.
 *
 * The bitstream only signals this 3 bits value but VPCodecConfigurationRecord requires the
 * three ISO/IEC 23001-8 codes, so each color space is mapped to its equivalent combination.
 * UNKNOWN and RESERVED are mapped to 2 (unspecified).
 */
enum class Vp9ColorSpace(
  val value: Int,
  val colourPrimaries: Int,
  private val transfer: Int,
  val matrixCoefficients: Int
) {
  UNKNOWN(0, 2, 2, 2),
  BT_601(1, 5, 6, 5),
  BT_709(2, 1, 1, 1),
  SMPTE_170(3, 6, 6, 6),
  SMPTE_240(4, 7, 7, 7),
  BT_2020(5, 9, 14, 9),
  RESERVED(6, 2, 2, 2),
  RGB(7, 1, 13, 0);

  fun getTransferCharacteristics(bitDepth: Int): Int {
    return if (this == BT_2020 && bitDepth == 12) 16 else transfer
  }

  companion object {
    fun fromValue(value: Int): Vp9ColorSpace = entries.firstOrNull { it.value == value } ?: UNKNOWN
  }
}
