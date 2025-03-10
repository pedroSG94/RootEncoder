package com.pedro.common.socket.ktor

import com.pedro.common.socket.base.TcpStreamSocket
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ReadWriteSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import java.net.InetAddress

abstract class TcpStreamSocketKtorBase(
    private val host: String,
    private val port: Int
): TcpStreamSocket() {

    private var socket: ReadWriteSocket? = null
    private var address: InetAddress? = null
    protected var input: ByteReadChannel? = null
    protected var output: ByteWriteChannel? = null
    protected var selectorManager = SelectorManager(Dispatchers.IO)

    abstract suspend fun onConnectSocket(timeout: Long): ReadWriteSocket

    override suspend fun connect() {
        val socket = onConnectSocket(timeout)
        input = socket.openReadChannel()
        output = socket.openWriteChannel(autoFlush = false)
        address = java.net.InetSocketAddress(host, port).address
        this.socket = socket
    }

    override suspend fun close() {
        try {
            address = null
            output?.flushAndClose()
            input = null
            output = null
            socket?.close()
            selectorManager.close()
        } catch (ignored: Exception) {}
    }

    override suspend fun write(bytes: ByteArray) {
        output?.writeFully(bytes)
    }

    override suspend fun write(bytes: ByteArray, offset: Int, size: Int) {
        output?.writeFully(bytes, offset, offset + size)
    }

    override suspend fun write(b: Int) {
        output?.writeByte(b.toByte())
    }

    override suspend fun write(string: String) {
        write(string.toByteArray())
    }

    override suspend fun flush() {
        output?.flush()
    }

    override suspend fun read(bytes: ByteArray) {
        input?.readFully(bytes)
    }

    override suspend fun read(size: Int): ByteArray {
        val data = ByteArray(size)
        read(data)
        return data
    }

    override suspend fun readLine(): String? = input?.readUTF8Line()

    override fun isConnected(): Boolean = socket?.isClosed != true

    override fun isReachable(): Boolean = address?.isReachable(timeout.toInt()) ?: false
}