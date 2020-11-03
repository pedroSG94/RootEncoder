package com.pedro.rtsp.utils

import android.util.Log
import java.io.IOException
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException

/**
 * Created by pedro on 25/02/17.
 *
 * this class is used for secure transport, to use replace socket on RtmpConnection with this and
 * you will have a secure stream under ssl/tls.
 */
object CreateSSLSocket {
  /**
   * @param host variable from RtspConnection
   * @param port variable from RtspConnection
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