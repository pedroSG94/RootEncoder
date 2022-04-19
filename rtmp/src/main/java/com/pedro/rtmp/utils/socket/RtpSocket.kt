package com.pedro.rtmp.utils.socket

import java.io.BufferedInputStream
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
abstract class RtpSocket {

  protected val timeout = 5000

  abstract fun getOutStream(): OutputStream
  abstract fun getInputStream(): InputStream
  abstract fun flush()
  abstract fun connect()
  abstract fun close()
  abstract fun isConnected(): Boolean
  abstract fun isReachable(): Boolean
}