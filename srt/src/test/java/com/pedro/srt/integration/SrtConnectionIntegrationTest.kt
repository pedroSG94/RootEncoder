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

package com.pedro.srt.integration

import com.pedro.common.ConnectChecker
import com.pedro.srt.srt.SrtClient
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
 * Integration test: connects to a real SRT server (MediaMTX in CI) and verifies the full
 * induction + conclusion handshake flow reaches onConnectionSuccess.
 *
 * It only exercises the handshake (no media is sent), so it needs neither a real encoder
 * (MediaCodec) nor an Android device — it runs on the plain JVM unit-test task.
 *
 * Skipped unless the env var SRT_INTEGRATION=true, so it is a no-op in the regular
 * `./gradlew test` build. The targets can be overridden with SRT_URL and SRT_URL_V6.
 * See .github/workflows/integration.yml for how the server is started in CI.
 *
 * Prefer running it isolated with --tests (like CI does): the client can report uncaught
 * coroutine exceptions after disconnect and kotlinx-coroutines-test flags them in unrelated
 * tests of the same JVM run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SrtConnectionIntegrationTest {

  @Before
  fun setup() {
    // SrtClient delivers its callbacks via withContext(Dispatchers.Main); on the JVM there is no
    // real Main dispatcher, so install one (Unconfined runs the callback inline).
    Dispatchers.setMain(Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `connect and publish to srt server`() {
    assumeTrue("integration disabled (set SRT_INTEGRATION=true to run)", System.getenv("SRT_INTEGRATION") == "true")
    val url = System.getenv("SRT_URL") ?: "srt://127.0.0.1:8890?streamid=publish:sinttest"
    connectAndAssert(url)
  }

  @Test
  fun `connect and publish to srt server by ipv6`() {
    assumeTrue("integration disabled (set SRT_INTEGRATION=true to run)", System.getenv("SRT_INTEGRATION") == "true")
    val url = System.getenv("SRT_URL_V6") ?: "srt://[::1]:8890?streamid=publish:sinttestv6"
    connectAndAssert(url)
  }

  private fun connectAndAssert(url: String) {
    val latch = CountDownLatch(1)
    val success = AtomicBoolean(false)
    val failReason = AtomicReference("")

    val client = SrtClient(object : ConnectChecker {
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
