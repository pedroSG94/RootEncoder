package com.pedro.rtmp.utils

import android.util.Log
import java.io.IOException
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException

/**
 * Created by pedro on 8/04/21.
 *
 * this class is used for secure transport, to use replace socket on RtmpClient with this and
 * you will have a secure stream under ssl/tls.
 */
object CreateSSLSocket {
  /**
   * @param host variable from RtmpClient
   * @param port variable from RtmpClient
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