/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.common

/**
 * Created by pedro on 23/9/24.
 */
enum class ConnectionFailed {
  ENDPOINT_MALFORMED, TIMEOUT, REFUSED, CLOSED_BY_SERVER, NO_INTERNET, UNKNOWN;

  companion object {
    fun parse(reason: String): ConnectionFailed {
      return if (
        reason.contains("network is unreachable", ignoreCase = true) ||
        reason.contains("software caused connection abort", ignoreCase = true) ||
        reason.contains("no route to host", ignoreCase = true)
      ) {
        NO_INTERNET
      } else if (reason.contains("broken pipe", ignoreCase = true)) {
        CLOSED_BY_SERVER
      } else if (reason.contains("connection refused", ignoreCase = true)) {
        REFUSED
      } else if (reason.contains("endpoint malformed", ignoreCase = true)) {
        ENDPOINT_MALFORMED
      } else if (
        reason.contains("timeout", ignoreCase = true) ||
        reason.contains("timed out", ignoreCase = true)
      ) {
        TIMEOUT
      } else {
        UNKNOWN
      }
    }
  }
}