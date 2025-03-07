package com.pedro.common.socket

import com.pedro.common.readUntil
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

abstract class TcpStreamSocketBase: StreamSocket() {

    private var socket = Socket()
    private var executorWrite = Executors.newSingleThreadExecutor()
    private var input = ByteArrayInputStream(byteArrayOf()).buffered()
    private var output = ByteArrayOutputStream().buffered()
    private var reader = InputStreamReader(input).buffered()
    private val semaphore = Semaphore(0)
    private val semaphoreTimeout = Semaphore(0)
    @Volatile
    private var crash: Exception? = null

    abstract fun onConnectSocket(timeout: Long): Socket

    override fun connect() {
        socket = onConnectSocket(timeout)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        output = socket.getOutputStream().buffered()
        input = socket.getInputStream().buffered()
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

    override fun close() {
        semaphore.release()
        executorWrite.shutdownNow()
        crash = null
        if (socket.isConnected) {
            runCatching { socket.shutdownOutput() }
            runCatching { socket.shutdownInput() }
            runCatching { socket.close() }
        }
    }

    fun write(bytes: ByteArray) {
        output.write(bytes)
    }

    fun write(bytes: ByteArray, offset: Int, size: Int) {
        output.write(bytes, offset, size)
    }

    fun write(b: Int) {
        output.write(b)
    }

    fun write(string: String) {
        write(string.toByteArray())
    }

    fun flush() {
        semaphore.release()
        val success = semaphoreTimeout.tryAcquire(timeout, TimeUnit.MILLISECONDS)
        if (!success) throw SocketTimeoutException("Flush timeout")
        crash?.let { throw it }
    }

    fun read(bytes: ByteArray) {
        input.readUntil(bytes)
    }

    fun read(size: Int): ByteArray {
        val data = ByteArray(size)
        read(data)
        return data
    }

    fun readLine(): String? = reader.readLine()

    override fun isConnected(): Boolean = socket.isConnected

    override fun isReachable(): Boolean = socket.inetAddress?.isReachable(timeout.toInt()) ?: false
}