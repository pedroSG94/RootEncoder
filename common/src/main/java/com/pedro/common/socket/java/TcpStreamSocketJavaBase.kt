package com.pedro.common.socket.java

import android.os.Build
import android.os.ParcelFileDescriptor
import com.pedro.common.readUntil
import com.pedro.common.socket.base.TcpStreamSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket

abstract class TcpStreamSocketJavaBase: TcpStreamSocket() {

    private val socketLock = Any()
    private var socket = Socket()
    private var input = ByteArrayInputStream(byteArrayOf()).buffered()
    private var output = ByteArrayOutputStream().buffered()

    abstract fun onConnectSocket(timeout: Long): Socket

    override suspend fun connect() {
        val connectedSocket = onConnectSocket(timeout)
        synchronized(socketLock) {
            socket = connectedSocket
            output = socket.getOutputStream().buffered()
            input = socket.getInputStream().buffered()
        }
    }

    override suspend fun close() {
        synchronized(socketLock) {
            if (socket.isConnected) {
                runCatching { socket.shutdownOutput() }
                runCatching { socket.shutdownInput() }
                runCatching { socket.close() }
            }
        }
    }

    override fun getTcpRtt(): Long? {
        // Before API 29, closing the ParcelFileDescriptor can close the live stream socket.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val descriptor = synchronized(socketLock) {
            if (!socket.isConnected || socket.isClosed) return null
            runCatching { ParcelFileDescriptor.fromSocket(socket) }.getOrNull()
        } ?: return null
        return runCatching {
            descriptor.use { TcpInfo.getRttMicros(it.fd) }
        }.getOrNull()
    }

    override suspend fun write(bytes: ByteArray) {
        output.write(bytes)
    }

    override suspend fun write(bytes: ByteArray, offset: Int, size: Int) {
        output.write(bytes, offset, size)
    }

    override suspend fun write(b: Int) {
        output.write(b)
    }

    override suspend fun write(string: String) {
        write(string.toByteArray())
    }

    override suspend fun flush() {
        output.flush()
    }

    override suspend fun read(bytes: ByteArray) {
        input.readUntil(bytes)
    }

    override suspend fun read(size: Int): ByteArray {
        val data = ByteArray(size)
        read(data)
        return data
    }

    override suspend fun readLine(): String? {
        var value = input.read()
        if (value == -1) return null
        val line = ByteArrayOutputStream()
        while (value != -1 && value != '\n'.code) {
            line.write(value)
            value = input.read()
        }
        var bytes = line.toByteArray()
        if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
            bytes = bytes.copyOf(bytes.size - 1)
        }
        return String(bytes)
    }

    override fun isConnected(): Boolean = socket.isConnected && !socket.isClosed

    override fun isReachable(): Boolean = socket.inetAddress?.isReachable(timeout.toInt()) ?: false
}