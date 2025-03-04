package com.pedro.common.socket

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketOptions

class UdpStreamSocket(
    private val host: String,
    private val port: Int,
    private val sourcePort: Int? = null,
    private val type: UdpType = UdpType.UNICAST
): StreamSocket() {

    private var socket: DatagramSocket? = null

    override fun connect() {
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
        socket.soTimeout = timeout.toInt()
        this@UdpStreamSocket.socket = socket
    }

    override fun close() {
        if (socket?.isClosed == false) {
            socket?.disconnect()
            socket?.close()
            socket = null
        }
    }

    fun write(bytes: ByteArray) {
        val udpPacket = DatagramPacket(bytes, bytes.size)
        socket?.send(udpPacket)
    }

    fun read(size: Int = SocketOptions.SO_RCVBUF): ByteArray {
        val buffer = ByteArray(size)
        val udpPacket = DatagramPacket(buffer, buffer.size)
        socket?.receive(udpPacket)
        return udpPacket.data.sliceArray(0 until udpPacket.length)
    }

    override fun isConnected(): Boolean = socket?.isConnected ?: false

    override fun isReachable(): Boolean = socket?.inetAddress?.isReachable(timeout.toInt()) ?: false
}