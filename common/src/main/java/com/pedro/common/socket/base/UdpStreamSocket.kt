package com.pedro.common.socket.base

abstract class UdpStreamSocket: StreamSocket() {

    abstract suspend fun bind()
    abstract suspend fun write(bytes: ByteArray)
    abstract suspend fun write(bytes: ByteArray, host: String, port: Int)
    abstract suspend fun read(): ByteArray
    abstract suspend fun readPacket(): UdpPacket
}