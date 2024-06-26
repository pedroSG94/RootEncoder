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

package com.pedro.srt.mpeg2ts

/**
 * Created by pedro on 20/8/23.
 *
 * PID (Packet Identifier)
 */
object Pid {

  const val MIN_VALUE = 32
  const val MAX_VALUE = 8186
  private var lastValue: Short = MIN_VALUE.toShort()

  @JvmStatic
  fun generatePID(): Short {
    val pid = lastValue
    if (pid >= MAX_VALUE) throw RuntimeException("Illegal pid")
    lastValue++
    return pid
  }

  @JvmStatic
  fun reset() {
    lastValue = MIN_VALUE.toShort()
  }
}