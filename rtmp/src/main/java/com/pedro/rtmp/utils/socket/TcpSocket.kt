package com.pedro.rtmp.utils.socket

import com.pedro.rtmp.utils.TLSSocketFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress

/**
 * Created by pedro on 5/4/22.
 */
class TcpSocket(private val host: String, private val port: Int, private val secured: Boolean): RtpSocket() {

  private lateinit var socket: Socket
  private lateinit var input: BufferedInputStream
  private lateinit var output: OutputStream

  override fun getOutStream(): OutputStream = output

  override fun getInputStream(): InputStream = input

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
      socket.connect(socketAddress, timeout)
    }
    output = socket.getOutputStream()
    input = BufferedInputStream(socket.getInputStream())
    socket.soTimeout = timeout
  }

  override fun close() {
    if (socket.isConnected) {
      socket.getInputStream().close()
      input.close()
      output.close()
      socket.close()
    }
  }

  override fun isConnected(): Boolean = socket.isConnected

  override fun isReachable(): Boolean = socket.inetAddress?.isReachable(5000) ?: false
}