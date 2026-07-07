package com.pedro.common.socket.java

import android.os.Build
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
                context.socketFactory.createSocket()
            } catch (e: GeneralSecurityException) {
                throw IOException("Create SSL socket failed: ${e.message}")
            }
        } else Socket()
        val socketAddress: SocketAddress = InetSocketAddress(host, port)
        socket.connect(socketAddress, timeout.toInt())
        socket.soTimeout = timeout.toInt()
        if (hostVerification && socket is SSLSocket && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            socket.sslParameters = socket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            socket.startHandshake()
        }
        return socket
    }
}
