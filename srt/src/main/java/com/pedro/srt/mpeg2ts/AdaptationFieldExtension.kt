/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.srt.mpeg2ts

/**
 * Created by pedro on 24/8/23.
 *
 * Header (2 bytes):
 *
 * Adaptation extension length -> 8 bits
 * Legal time window (LTW) flag -> 1 bit
 * Piecewise rate flag -> 1 bit
 * Seamless splice flag -> 1 bit
 * Reserved -> 5 bits
 *
 * Optional fields
 *
 * LTW flag set (2 bytes)
 * LTW valid flag -> 1 bit
 * LTW offset -> 15 bits
 *
 * Piecewise flag set (3 bytes)
 * Reserved -> 2 bits
 * Piecewise rate -> 22 bits
 *
 * Seamless splice flag set (5 bytes)
 * Splice type -> 4 bits
 * DTS next access unit -> 36 bits
 */
class AdaptationFieldExtension {
  private val length: Byte = 0
  private val legalTimeWindow: Boolean = false
  private val piecewiseRateFlag: Boolean = false
  private val seamlessSpliceFlag: Boolean = false
  private val reserved: Byte = 0
  //optionals
  private val ltwValidFlag: Boolean = false
  private val ltwOffset: Short = 0

  private val piecewiseReserved: Byte = 0
  private val piecewiseRate: Int = 0

  private val spliceType: Byte = 0
  private val dtsNextAccessUnit: Long = 0
}