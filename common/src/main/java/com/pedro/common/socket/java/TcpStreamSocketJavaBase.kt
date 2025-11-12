package com.pedro.common.socket.java

import com.pedro.common.readUntil
import com.pedro.common.socket.base.TcpStreamSocket
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Socket

abstract class TcpStreamSocketJavaBase: TcpStreamSocket() {

    private var socket = Socket()
    private var input = ByteArrayInputStream(byteArrayOf()).buffered()
    private var output = ByteArrayOutputStream().buffered()
    private var reader = InputStreamReader(input).buffered()

    abstract fun onConnectSocket(timeout: Long): Socket

    override suspend fun connect() {
        socket = onConnectSocket(timeout)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        output = socket.getOutputStream().buffered()
        input = socket.getInputStream().buffered()
    }

    override suspend fun close() {
        if (socket.isConnected) {
            runCatching { socket.shutdownOutput() }
            runCatching { socket.shutdownInput() }
            runCatching { socket.close() }
        }
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

    override suspend fun readLine(): String? = reader.readLine()

    override fun isConnected(): Boolean = socket.isConnected

    override fun isReachable(): Boolean = socket.inetAddress?.isReachable(timeout.toInt()) ?: false
}