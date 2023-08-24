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

package com.pedro.srt.mpeg2ts.psi

/**
 * Created by pedro on 20/8/23.
 *
 * PSI (Program Specific Information)
 *
 * Header (3 bytes):
 *
 * Table ID -> 8 bits
 * Section syntax indicator -> 1 bit
 * Private bit -> 1 bit
 * Reserved bits -> 2 bits
 * Section length unused bits -> 2 bits
 * Section length -> 10 bits
 *
 * Syntax section/Table data -> N*8 bits
 */
abstract class PSI {
  private val tableId: Byte = 0
  private val sectionSyntaxIndicator: Boolean = false
  private val reserved: Byte = 0
  private val sectionLengthUnusedBits: Byte = 0
  private val sectionLength: Short = 0
}