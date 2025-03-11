package com.pedro.common.socket.java

import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.socket.base.UdpType
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketOptions

class UdpStreamSocketJava(
    private val host: String,
    private val port: Int,
    private val sourcePort: Int? = null,
    receiveSize: Int? = null,
    private val type: UdpType = UdpType.UNICAST
): UdpStreamSocket() {

    private var socket: DatagramSocket? = null
    private val packetSize = receiveSize ?: SocketOptions.SO_RCVBUF

    override suspend fun connect() {
        val socket = when (type) {
            UdpType.UNICAST -> {
                sourcePort?.let { DatagramSocket(sourcePort) } ?: DatagramSocket()
            }
            UdpType.MULTICAST -> {
                sourcePort?.let { MulticastSocket(sourcePort) } ?: MulticastSocket()
            }
            UdpType.BROADCAST -> {
                val socket = sourcePort?.let { DatagramSocket(sourcePort) } ?: DatagramSocket()
                socket.apply { broadcast = true }
            }
        }
        val address = InetAddress.getByName(host)
        socket.connect(address, port)
        socket.receiveBufferSize = packetSize
        socket.soTimeout = timeout.toInt()
        this.socket = socket
    }

    override suspend fun close() {
        if (socket?.isClosed == false) {
            socket?.disconnect()
            socket?.close()
            socket = null
        }
    }

    override suspend fun write(bytes: ByteArray) {
        val udpPacket = DatagramPacket(bytes, bytes.size)
        socket?.send(udpPacket)
    }

    override suspend fun read(): ByteArray {
        val buffer = ByteArray(packetSize)
        val udpPacket = DatagramPacket(buffer, buffer.size)
        socket?.receive(udpPacket)
        return udpPacket.data.sliceArray(0 until udpPacket.length)
    }

    override fun isConnected(): Boolean = socket?.isConnected ?: false

    override fun isReachable(): Boolean = socket?.inetAddress?.isReachable(timeout.toInt()) ?: false
}