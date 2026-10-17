/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.common.socket.java

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class TcpStreamSocketJavaBaseTest {

  @Test
  fun `GIVEN a Java socket WHEN connecting and closing THEN expose only the active socket`() = runTest {
    ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { serverSocket ->
      val streamSocket = TestTcpStreamSocket(serverSocket)

      assertNull(streamSocket.getJavaSocket())

      streamSocket.connect()
      serverSocket.accept().use {
        assertSame(streamSocket.connectedSocket, streamSocket.getJavaSocket())

        streamSocket.close()
        assertNull(streamSocket.getJavaSocket())
      }
    }
  }

  private class TestTcpStreamSocket(
    private val serverSocket: ServerSocket,
  ): TcpStreamSocketJavaBase() {

    lateinit var connectedSocket: Socket
      private set

    override fun onConnectSocket(timeout: Long): Socket {
      return Socket(serverSocket.inetAddress, serverSocket.localPort).also {
        connectedSocket = it
      }
    }
  }
}
