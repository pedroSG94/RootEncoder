/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.rtsp.utils

import android.util.Log
import java.io.IOException
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException

/**
 * Created by pedro on 25/02/17.
 *
 * this class is used for secure transport, to use replace socket on RtspClient with this and
 * you will have a secure stream under ssl/tls.
 */
object CreateSSLSocket {
  /**
   * @param host variable from RtspClient
   * @param port variable from RtspClient
   */
  @JvmStatic
  fun createSSlSocket(host: String, port: Int): Socket? {
    return try {
      val socketFactory = TLSSocketFactory()
      socketFactory.createSocket(host, port)
    } catch (e: NoSuchAlgorithmException) {
      Log.e("CreateSSLSocket", "Error", e)
      null
    } catch (e: KeyManagementException) {
      Log.e("CreateSSLSocket", "Error", e)
      null
    } catch (e: IOException) {
      Log.e("CreateSSLSocket", "Error", e)
      null
    }
  }
}