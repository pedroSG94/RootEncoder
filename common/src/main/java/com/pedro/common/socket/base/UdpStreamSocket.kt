package com.pedro.common.socket.base

abstract class UdpStreamSocket: StreamSocket() {
    abstract suspend fun write(bytes: ByteArray)
    abstract suspend fun read(): ByteArray
}