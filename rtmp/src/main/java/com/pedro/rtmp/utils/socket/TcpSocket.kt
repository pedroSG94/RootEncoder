package com.pedro.rtmp.utils.socket

import com.pedro.rtmp.utils.TLSSocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress

/**
 * Created by pedro on 5/4/22.
 */
class TcpSocket(private val host: String, private val port: Int, private val secured: Boolean): RtpSocket() {

  private var socket = Socket()

  override fun getOutStream(): OutputStream = socket.getOutputStream()

  override fun getInputStream(): InputStream = socket.getInputStream()

  override fun flush() {
    getOutStream().flush()
  }

  override fun connect() {
    if (secured) {
      val socketFactory = TLSSocketFactory()
      socket = socketFactory.createSocket(host, port)
    } else {
      socket = Socket()
      val socketAddress: SocketAddress = InetSocketAddress(host, port)
      socket.connect(socketAddress)
    }
    socket.soTimeout = 5000
  }

  override fun close() {
    if (socket.isConnected) socket.close()
  }

  override fun isConnected(): Boolean = socket.isConnected

  override fun isReachable(): Boolean = socket.inetAddress?.isReachable(5000) ?: false
}