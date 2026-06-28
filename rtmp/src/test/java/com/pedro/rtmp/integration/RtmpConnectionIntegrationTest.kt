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

package com.pedro.rtmp.integration

import com.pedro.common.ConnectChecker
import com.pedro.rtmp.rtmp.RtmpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration test: connects to a real RTMP server (MediaMTX in CI) and verifies the full
 * handshake + connect + createStream + publish flow reaches onConnectionSuccess.
 *
 * It only exercises the connection/publish handshake (no media is sent), so it needs neither
 * a real encoder (MediaCodec) nor an Android device — it runs on the plain JVM unit-test task.
 *
 * Skipped unless the env var RTMP_INTEGRATION=true, so it is a no-op in the regular
 * `./gradlew test` build. The target can be overridden with RTMP_URL.
 * See .github/workflows/integration.yml for how the server is started in CI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RtmpConnectionIntegrationTest {

  @Before
  fun setup() {
    // RtmpClient delivers its callbacks via withContext(Dispatchers.Main); on the JVM there is no
    // real Main dispatcher, so install one (Unconfined runs the callback inline).
    Dispatchers.setMain(Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `connect and publish to rtmp server`() {
    assumeTrue("integration disabled (set RTMP_INTEGRATION=true to run)", System.getenv("RTMP_INTEGRATION") == "true")
    val url = System.getenv("RTMP_URL") ?: "rtmp://127.0.0.1:1935/live/test"

    val latch = CountDownLatch(1)
    val success = AtomicBoolean(false)
    val failReason = AtomicReference("")

    val client = RtmpClient(object : ConnectChecker {
      override fun onConnectionStarted(url: String) {}
      override fun onConnectionSuccess() {
        success.set(true)
        latch.countDown()
      }
      override fun onConnectionFailed(reason: String) {
        failReason.set(reason)
        latch.countDown()
      }
      override fun onNewBitrate(bitrate: Long) {}
      override fun onDisconnect() {}
      override fun onAuthError() {
        failReason.set("auth error")
        latch.countDown()
      }
      override fun onAuthSuccess() {}
    })

    try {
      client.connect(url)
      assertTrue("no connection callback within timeout for $url", latch.await(20, TimeUnit.SECONDS))
      assertTrue("connection to $url failed: ${failReason.get()}", success.get())
    } finally {
      client.disconnect()
    }
  }
}
