package com.pedro.common.socket.java

import com.pedro.common.socket.base.UdpPacket
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
    private val sourceHost: String? = null,
    private val sourcePort: Int? = null,
    receiveSize: Int? = null,
    private val type: UdpType = UdpType.UNICAST
): UdpStreamSocket() {

    private var socket: DatagramSocket? = null
    private val packetSize = receiveSize ?: SocketOptions.SO_RCVBUF
    private var remoteHost: String? = null
    private var remotePort: Int? = null

    override suspend fun connect() {
        val socket = when (type) {
            UdpType.UNICAST -> {
                sourcePort?.let {
                    val address = if (sourceHost != null) InetAddress.getByName(sourceHost) else null
                    if (address != null) DatagramSocket(sourcePort, address) else DatagramSocket(sourcePort)
                } ?: DatagramSocket()
            }
            UdpType.MULTICAST -> {
                sourcePort?.let { MulticastSocket(sourcePort) } ?: MulticastSocket()
            }
            UdpType.BROADCAST -> {
                val socket = sourcePort?.let {
                    val address = if (sourceHost != null) InetAddress.getByName(sourceHost) else null
                    if (address != null) DatagramSocket(sourcePort, address) else DatagramSocket(sourcePort)
                } ?: DatagramSocket()
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

    override suspend fun bind() {
        val socket = when (type) {
            UdpType.UNICAST -> {
                DatagramSocket(port, InetAddress.getByName(host))
            }
            UdpType.MULTICAST -> {
                MulticastSocket(port)
            }
            UdpType.BROADCAST -> {
                val socket = DatagramSocket(port, InetAddress.getByName(host))
                socket.apply { broadcast = true }
            }
        }
        socket.receiveBufferSize = packetSize
        socket.soTimeout = timeout.toInt()
        this.socket = socket
    }

    override suspend fun write(bytes: ByteArray) {
        val udpPacket = if (remoteHost != null && remotePort != null) {
            DatagramPacket(bytes, bytes.size, InetAddress.getByName(remoteHost!!), remotePort!!)
        } else {
            DatagramPacket(bytes, bytes.size)
        }
        socket?.send(udpPacket)
    }

    override suspend fun write(bytes: ByteArray, host: String, port: Int) {
        val udpPacket = DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port)
        socket?.send(udpPacket)
    }

    override suspend fun read(): ByteArray {
        val packet = readPacket()
        return packet.data.sliceArray(0 until packet.size)
    }

    override suspend fun readPacket(): UdpPacket {
        val buffer = ByteArray(packetSize)
        val udpPacket = DatagramPacket(buffer, buffer.size)
        socket?.receive(udpPacket)
        return UdpPacket(udpPacket.data, udpPacket.length, udpPacket.address.hostName, udpPacket.port)
    }

    override suspend fun setRemoteAddress(host: String, port: Int) {
        this.remoteHost = host
        this.remotePort = port
    }

    override suspend fun getLocalHost(): String {
        return sourceHost ?: "0.0.0.0"
    }

    override suspend fun getLocalPort(): Int {
        return sourcePort ?: 0
    }

    override fun isConnected(): Boolean = socket?.isConnected ?: false

    override fun isReachable(): Boolean = socket?.inetAddress?.isReachable(timeout.toInt()) ?: false
}