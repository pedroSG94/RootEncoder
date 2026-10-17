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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class TcpStreamSocketJavaTest {

  private val ipv4 = InetAddress.getByName("127.0.0.1")
  // Nothing listens on ::1 in these tests (the server binds 127.0.0.1 only), so a connect there fails
  // at once, the same way a refused or unusable IPv6 address does.
  private val ipv6 = InetAddress.getByName("::1")

  @Test
  fun `falls back to the next address when the first one fails`() {
    ServerSocket(0, 1, ipv4).use { server ->
      val socket = TcpStreamSocketJava("example.test", server.localPort, secured = false)
      socket.resolver = { arrayOf(ipv6, ipv4) }
      val connected = socket.onConnectSocket(3000)
      try {
        assertTrue(connected.isConnected)
        assertEquals(ipv4, connected.inetAddress)
      } finally {
        connected.close()
      }
    }
  }

  @Test
  fun `uses the first address when it works`() {
    ServerSocket(0, 1, ipv4).use { server ->
      val socket = TcpStreamSocketJava("example.test", server.localPort, secured = false)
      socket.resolver = { arrayOf(ipv4, ipv6) }
      val connected = socket.onConnectSocket(3000)
      try {
        assertEquals(ipv4, connected.inetAddress)
      } finally {
        connected.close()
      }
    }
  }

  @Test
  fun `keeps the host name for the connected address`() {
    ServerSocket(0, 1, ipv4).use { server ->
      val socket = TcpStreamSocketJava("example.test", server.localPort, secured = false)
      socket.resolver = { arrayOf(ipv4) }
      val connected = socket.onConnectSocket(3000)
      try {
        assertEquals("example.test", connected.inetAddress.hostName)
      } finally {
        connected.close()
      }
    }
  }

  @Test
  fun `throws when no address connects`() {
    val closedPort = ServerSocket(0, 1, ipv4).use { it.localPort }
    val socket = TcpStreamSocketJava("example.test", closedPort, secured = false)
    socket.resolver = { arrayOf(ipv6, ipv4) }
    try {
      socket.onConnectSocket(2000)
      fail("connect should fail when every address fails")
    } catch (e: IOException) {
      // expected
    }
  }
}
