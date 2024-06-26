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

package com.pedro.rtmp.utils.socket

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 5/4/22.
 * Socket implementation that accept:
 * - TCP
 * - TCP SSL/TLS
 * - UDP
 * - Tunneled HTTP
 * - Tunneled HTTPS
 */
abstract class RtmpSocket {

  protected val timeout = 5000

  abstract fun getOutStream(): OutputStream
  abstract fun getInputStream(): InputStream
  abstract fun flush(isPacket: Boolean = false)
  abstract fun connect()
  abstract fun close()
  abstract fun isConnected(): Boolean
  abstract fun isReachable(): Boolean
}