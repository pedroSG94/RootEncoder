package com.pedro.common.socket.base

abstract class TcpStreamSocket: StreamSocket() {
    /**
     * Returns the smoothed TCP round-trip time reported by the kernel, in microseconds.
     *
     * This is only available for eligible socket implementations that expose TCP_INFO.
     */
    open fun getTcpRtt(): Long? = null

    abstract suspend fun write(bytes: ByteArray)
    abstract suspend fun write(bytes: ByteArray, offset: Int, size: Int)
    abstract suspend fun write(b: Int)
    abstract suspend fun write(string: String)
    abstract suspend fun flush()
    abstract suspend fun read(bytes: ByteArray)
    abstract suspend fun read(size: Int): ByteArray
    abstract suspend fun readLine(): String?

}