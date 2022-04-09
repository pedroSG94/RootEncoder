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
abstract class RtpSocket {

  enum class TcpProtocol {
    TCP, TCP_SSL_TLS, TUNNELED_HTTP, TUNNELED_HTTPS
  }

  companion object {
    fun createTcpSocket(protocol: TcpProtocol, host: String, port: Int): RtpSocket? {
      return when (protocol) {
        TcpProtocol.TCP -> TcpSocket(host, port, false)
        TcpProtocol.TCP_SSL_TLS -> TcpSocket(host, port, true)
        TcpProtocol.TUNNELED_HTTP -> TcpTunneledSocket(host, port, false)
        TcpProtocol.TUNNELED_HTTPS -> TcpTunneledSocket(host, port, true)
      }
    }

    fun createUdpSocket(host: String, ports: Array<Int>): RtpSocket? {
      return null
    }
  }

  abstract fun getOutStream(): OutputStream?
  abstract fun getInputStream(): InputStream?
  abstract fun connect()
  abstract fun close()
  abstract fun isConnected(): Boolean
  abstract fun isReachable(): Boolean
}