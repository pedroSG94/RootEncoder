package com.pedro.common.socket.java

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

class TcpStreamSocketJava(
    private val host: String,
    private val port: Int,
    private val secured: Boolean,
    private val certificates: TrustManager? = null
): TcpStreamSocketJavaBase() {

    override fun onConnectSocket(timeout: Long): Socket {
        val socket = if (secured) {
            try {
                val context = SSLContext.getInstance("TLS")
                context.init(null, certificates?.let { arrayOf(it) }, SecureRandom())
                val socket = context.socketFactory.createSocket()
                if (socket is SSLSocket) socket.enabledProtocols = arrayOf("TLSv1.1", "TLSv1.2")
                socket
            } catch (e: GeneralSecurityException) {
                throw IOException("Create SSL socket failed: ${e.message}")
            }
        } else Socket()
        val socketAddress: SocketAddress = InetSocketAddress(host, port)
        socket.connect(socketAddress, timeout.toInt())
        socket.soTimeout = timeout.toInt()
        return socket
    }
}