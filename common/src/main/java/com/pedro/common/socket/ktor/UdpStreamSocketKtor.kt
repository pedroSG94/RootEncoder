package com.pedro.common.socket.ktor

import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.socket.base.UdpType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ConnectedDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.Dispatchers
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.net.ConnectException
import java.net.InetAddress

class UdpStreamSocketKtor(
    private val host: String,
    private val port: Int,
    private val sourcePort: Int? = null,
    private val receiveSize: Int? = null,
    private val type: UdpType = UdpType.UNICAST
): UdpStreamSocket() {

    private val address = InetSocketAddress(host, port)
    private var selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: ConnectedDatagramSocket? = null
    private var myAddress: InetAddress? = null

    override suspend fun connect() {
        selectorManager = SelectorManager(Dispatchers.IO)
        val builder = aSocket(selectorManager).udp()
        val localAddress = if (sourcePort == null) null else InetSocketAddress("0.0.0.0", sourcePort)
        val socket = builder.connect(
            remoteAddress = address,
            localAddress = localAddress
        ) {
            broadcast = type == UdpType.BROADCAST
            receiveBufferSize = receiveSize ?: 0
        }
        myAddress = java.net.InetSocketAddress(host, port).address
        this.socket = socket
    }

    override suspend fun close() {
        try {
            socket?.close()
            selectorManager.close()
        } catch (ignored: Exception) {}
    }

    override suspend fun write(bytes: ByteArray) {
        val datagram = Datagram(Buffer().apply { write(bytes) }, address)
        socket?.send(datagram)
    }

    override suspend fun read(): ByteArray {
        val socket = socket ?: throw ConnectException("Read with socket closed, broken pipe")
        val packet = socket.receive().packet
        val length = packet.remaining.toInt()
        return packet.readByteArray().sliceArray(0 until length)
    }

    override fun isConnected(): Boolean = socket?.isClosed != true

    override fun isReachable(): Boolean = myAddress?.isReachable(timeout.toInt()) ?: false
}