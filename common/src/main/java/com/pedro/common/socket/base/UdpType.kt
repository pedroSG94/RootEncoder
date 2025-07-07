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

package com.pedro.common.socket.base

/**
 * Created by pedro on 6/3/24.
 */
enum class UdpType {
  UNICAST, MULTICAST, BROADCAST;

  companion object {
    fun getTypeByHost(host: String): UdpType {
      val firstNumber = host.split(".")[0].toIntOrNull()
      return when (firstNumber) {
        in 224..239 -> MULTICAST
        255 -> BROADCAST
        else -> UNICAST
      }
    }
  }
}