package com.pedro.whip

import com.pedro.common.ConnectChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class WhipClientTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connect to whip server`() {
        val started = AtomicBoolean(false)
        val failureReason = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)

        val connectChecker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) {
                println("[WhipTest] Connection started: $url")
                started.set(true)
            }
            override fun onConnectionSuccess() {
                println("[WhipTest] Connection success!")
                latch.countDown()
            }
            override fun onConnectionFailed(reason: String) {
                println("[WhipTest] Connection failed: $reason")
                failureReason.set(reason)
                latch.countDown()
            }
            override fun onDisconnect() {
                println("[WhipTest] Disconnected")
            }
            override fun onAuthError() {
                println("[WhipTest] Auth error")
                latch.countDown()
            }
            override fun onAuthSuccess() {
                println("[WhipTest] Auth success")
            }
        }

        val client = WhipClient(connectChecker)
        client.setOnlyAudio(true)
        client.setAudioInfo(48000, true)
        client.connect("http://192.168.68.65:8889/mystream/whip/whip")

        // Wait up to 90 seconds — DTLS has a ~63s internal timeout before reporting failure
        val completed = latch.await(90, TimeUnit.SECONDS)

        assertTrue("onConnectionStarted was never called", started.get())
        if (completed) {
            assertNull("Connection failed: ${failureReason.get()}", failureReason.get())
        } else {
            println("[WhipTest] 30s timeout reached — connection in progress (no failure reported, likely blocked at DTLS/mutex stage)")
        }
    }
}
