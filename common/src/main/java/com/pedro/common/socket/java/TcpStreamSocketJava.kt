package com.pedro.common.socket.java

import android.os.Build
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
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

    /**
     * Resolves the host to every address it has. Replaceable in tests.
     */
    internal var resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }

    /**
     * Tries every address the host resolves to, in the order the system returns them, instead of
     * only the first one. A host with both IPv6 and IPv4 addresses usually resolves IPv6 first; on a
     * network where IPv6 is assigned but broken that address refuses or times out, and the stream
     * used to fail even though the same server was reachable over IPv4.
     * The whole attempt keeps the original timeout: every address gets an equal share of the time
     * that is left, so a slow address can't use up the time of the ones after it.
     */
    override fun onConnectSocket(timeout: Long): Socket {
        return configure(connectAny(timeout), timeout)
    }

    /**
     * Only the TCP connect moves on to the next address. A TLS handshake failure is not about the
     * address, so it is reported as it is instead of being replaced by the error of the last address.
     */
    private fun connectAny(timeout: Long): Socket {
        val addresses = resolver(host)
        val deadline = System.currentTimeMillis() + timeout
        var lastError: IOException? = null
        for ((index, address) in addresses.withIndex()) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val attemptTimeout = remaining / (addresses.size - index)
            val socket = createSocket()
            try {
                // Keeping the host name in the address keeps it for SNI and certificate checks.
                val endpoint = InetSocketAddress(InetAddress.getByAddress(host, address.address), port)
                socket.connect(endpoint, attemptTimeout.toInt().coerceAtLeast(1))
                return socket
            } catch (e: IOException) {
                runCatching { socket.close() }
                lastError = e
            }
        }
        throw lastError ?: IOException("Unable to connect to $host")
    }

    private fun createSocket(): Socket {
        return if (secured) {
            try {
                val context = SSLContext.getInstance("TLS")
                context.init(null, certificates?.let { arrayOf(it) }, SecureRandom())
                context.socketFactory.createSocket()
            } catch (e: GeneralSecurityException) {
                throw IOException("Create SSL socket failed: ${e.message}")
            }
        } else Socket()
    }

    private fun configure(socket: Socket, timeout: Long): Socket {
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
