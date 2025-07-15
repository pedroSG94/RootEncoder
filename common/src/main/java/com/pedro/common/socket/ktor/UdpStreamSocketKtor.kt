package com.pedro.common.socket.ktor

import com.pedro.common.socket.base.UdpPacket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.socket.base.UdpType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ASocket
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.ConnectedDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.DatagramReadWriteChannel
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
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
    private val sourceHost: String? = null,
    private val sourcePort: Int? = null,
    private val receiveSize: Int? = null,
    private val type: UdpType = UdpType.UNICAST
): UdpStreamSocket() {

    private val address = InetSocketAddress(host, port)
    private var selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: DatagramReadWriteChannel? = null
    private var myAddress: InetAddress? = null

    override suspend fun connect() {
        selectorManager = SelectorManager(Dispatchers.IO)
        val builder = aSocket(selectorManager).udp()
        val localAddress = if (sourcePort == null) null else InetSocketAddress(sourceHost ?: "0.0.0.0", sourcePort)
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
        runCatching {
            (socket as? BoundDatagramSocket)?.close()
            (socket as? ConnectedDatagramSocket)?.close()
            selectorManager.close()
        }
    }

    override suspend fun bind() {
        selectorManager = SelectorManager(Dispatchers.IO)
        val builder = aSocket(selectorManager).udp()
        val socket = builder.bind(
            localAddress = InetSocketAddress(host, port)
        ) {
            broadcast = type == UdpType.BROADCAST
            receiveBufferSize = receiveSize ?: 0
        }
        myAddress = java.net.InetSocketAddress(host, port).address
        this.socket = socket
    }

    override suspend fun write(bytes: ByteArray) {
        val datagram = Datagram(Buffer().apply { write(bytes, 0, bytes.size) }, address)
        socket?.send(datagram)
    }

    override suspend fun write(bytes: ByteArray, host: String, port: Int) {
        val datagram = Datagram(Buffer().apply { write(bytes, 0, bytes.size) }, InetSocketAddress(host, port))
        socket?.send(datagram)
    }

    override suspend fun read(): ByteArray {
        val packet = readPacket()
        return packet.data.sliceArray(0 until packet.size)
    }

    override suspend fun readPacket(): UdpPacket {
        val socket = socket ?: throw ConnectException("Read with socket closed, broken pipe")
        val datagram = socket.receive()
        val length = datagram.packet.remaining.toInt()
        val data = datagram.packet.readByteArray()
        val address = datagram.address as? InetSocketAddress
        return UdpPacket(data, length, address?.hostname, address?.port)
    }

    override fun isConnected(): Boolean = (socket as? ASocket)?.isClosed != true

    override fun isReachable(): Boolean = myAddress?.isReachable(timeout.toInt()) ?: false
}