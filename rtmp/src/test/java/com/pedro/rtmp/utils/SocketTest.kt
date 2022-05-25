package com.pedro.rtmp.utils

import com.pedro.rtmp.utils.socket.TcpSocket
import org.junit.Test

class SocketTest {

    @Test
    fun `check tcp socket error with socket not connected`() {
        val socket = TcpSocket("127.0.0.1", 1935, false)
        socket.getOutStream().write(0)
        socket.getInputStream()
        socket.close()
    }
}