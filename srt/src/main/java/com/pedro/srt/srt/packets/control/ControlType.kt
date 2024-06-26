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

package com.pedro.srt.srt.packets.control

import java.io.IOException

/**
 * Created by pedro on 21/8/23.
 */
enum class ControlType(val value: Int) {
  HANDSHAKE(0), KEEP_ALIVE(1), ACK(2), NAK(3),
  CONGESTION_WARNING(4), SHUTDOWN(5), ACK2(6),
  DROP_REQ(7), PEER_ERROR(8), USER_DEFINED(0x7FFF),
  SUB_TYPE(0);

  companion object {
    infix fun from(value: Int): ControlType = entries.firstOrNull { it.value == value } ?: throw IOException("unknown control type: $value")
  }
}