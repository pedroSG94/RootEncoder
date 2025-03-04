package com.pedro.common.socket

import com.pedro.common.TLSSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.security.GeneralSecurityException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManager

class TcpStreamSocket(
    private val host: String,
    private val port: Int,
    private val secured: Boolean,
    private val certificates: TrustManager? = null
): StreamSocket() {

    private var socket = Socket()
    private var executorWrite = Executors.newSingleThreadExecutor()
    private var input = ByteArrayInputStream(byteArrayOf()).buffered()
    private var output = ByteArrayOutputStream().buffered()
    private var reader = InputStreamReader(input).buffered()
    private var writer = OutputStreamWriter(output).buffered()
    private val semaphore = Semaphore(0)
    private val semaphoreTimeout = Semaphore(0)
    @Volatile
    private var crash: Exception? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (secured) {
            try {
                val socketFactory = TLSSocketFactory(certificates?.let { arrayOf(it) })
                socket = socketFactory.createSocket(host, port)
            } catch (e: GeneralSecurityException) {
                throw IOException("Create SSL socket failed: ${e.message}")
            }
        } else {
            socket = Socket()
            val socketAddress: SocketAddress = InetSocketAddress(host, port)
            socket.connect(socketAddress, timeout.toInt())
        }
        output = socket.getOutputStream().buffered()
        input = socket.getInputStream().buffered()
        reader = InputStreamReader(input).buffered()
        writer = OutputStreamWriter(output).buffered()
        socket.soTimeout = timeout.toInt()
        //parallel thread to do output flush allowing have a flush timeout and avoid stuck on it
        executorWrite = Executors.newSingleThreadExecutor()
        executorWrite.execute {
            try {
                doFlush()
            } catch (e: Exception) {
                crash = e
                semaphoreTimeout.release()
            }
        }
    }

    private fun doFlush() {
        while (socket.isConnected) {
            semaphore.acquire()
            output.flush()
            semaphoreTimeout.release()
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        semaphore.release()
        executorWrite.shutdownNow()
        crash = null
        if (socket.isConnected) {
            runCatching { socket.shutdownOutput() }
            runCatching { socket.shutdownInput() }
            runCatching { socket.close() }
        }
    }

    suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        output.write(bytes)
    }

    suspend fun write(bytes: ByteArray, offset: Int, size: Int) = withContext(Dispatchers.IO) {
        output.write(bytes, offset, size)
    }

    suspend fun write(b: Int) = withContext(Dispatchers.IO) {
        output.write(b)
    }

    suspend fun write(string: String) = withContext(Dispatchers.IO) {
        writer.write(string)
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        semaphore.release()
        val success = semaphoreTimeout.tryAcquire(timeout, TimeUnit.MILLISECONDS)
        if (!success) throw SocketTimeoutException("Flush timeout")
        crash?.let { throw it }
    }

    suspend fun read(bytes: ByteArray) = withContext(Dispatchers.IO) {
        input.read(bytes)
    }

    suspend fun read(size: Int): ByteArray = withContext(Dispatchers.IO) {
        val data = ByteArray(size)
        input.read(data)
        data
    }

    suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        reader.readLine()
    }

    override fun isConnected(): Boolean = socket.isConnected

    override fun isReachable(): Boolean = socket.inetAddress?.isReachable(timeout.toInt()) ?: false
}