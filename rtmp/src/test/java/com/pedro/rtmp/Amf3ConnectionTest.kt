/*
 * Copyright (C) 2026 pedroSG94.
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

package com.pedro.rtmp

import com.pedro.common.ConnectChecker
import com.pedro.rtmp.amf.AmfVersion
import com.pedro.rtmp.rtmp.CommandsManagerAmf0
import com.pedro.rtmp.rtmp.CommandsManagerAmf3
import com.pedro.rtmp.rtmp.RtmpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests against real RTMP servers, skipped unless the env var is set:
 *
 * AMF3 session (server with AMF3 support, e.g. Red5):
 * RTMP_AMF3_URL=rtmp://localhost/live/test ./gradlew :rtmp:testDebugUnitTest --tests "*Amf3ConnectionTest*"
 *
 * AMF0 fallback (server without AMF3, e.g. MediaMTX):
 * RTMP_AMF3_FALLBACK_URL=rtmp://localhost:1936/live/test ./gradlew :rtmp:testDebugUnitTest --tests "*Amf3ConnectionTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Amf3ConnectionTest {

  @Test
  fun `amf3 session against a server with amf3 support`() {
    val url = System.getenv("RTMP_AMF3_URL")
    assumeTrue("skipped: RTMP_AMF3_URL not set", url != null)
    val client = connectAndAwaitPublish(url ?: "")
    assertTrue("server negotiated amf3, manager should still be amf3",
      getCommandsManager(client) is CommandsManagerAmf3)
    client.disconnect()
  }

  @Test
  fun `amf0 fallback against a server without amf3 support`() {
    val url = System.getenv("RTMP_AMF3_FALLBACK_URL")
    assumeTrue("skipped: RTMP_AMF3_FALLBACK_URL not set", url != null)
    val client = connectAndAwaitPublish(url ?: "")
    assertTrue("server without amf3, manager should have fallen back to amf0",
      getCommandsManager(client) is CommandsManagerAmf0)
    client.disconnect()
  }

  private fun connectAndAwaitPublish(url: String): RtmpClient {
    Dispatchers.setMain(Dispatchers.Default)
    val latch = CountDownLatch(1)
    var failedReason: String? = null
    val client = RtmpClient(object : ConnectChecker {
      override fun onConnectionStarted(url: String) {}
      override fun onConnectionSuccess() { latch.countDown() }
      override fun onConnectionFailed(reason: String) {
        failedReason = reason
        latch.countDown()
      }
      override fun onDisconnect() {}
      override fun onAuthError() {}
      override fun onAuthSuccess() {}
    })
    client.setAmfVersion(AmfVersion.VERSION_3)
    client.connect(url)
    val responded = latch.await(10, TimeUnit.SECONDS)
    if (!responded) client.disconnect()
    assertTrue("no connection result in 10s", responded)
    assertNull("connection failed: $failedReason", failedReason)
    return client
  }

  private fun getCommandsManager(client: RtmpClient): Any {
    val field = RtmpClient::class.java.getDeclaredField("commandsManager")
    field.isAccessible = true
    return field.get(client)
  }
}
