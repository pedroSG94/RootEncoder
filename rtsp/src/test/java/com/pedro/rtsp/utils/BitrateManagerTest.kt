package com.pedro.rtsp.utils

import junit.framework.Assert.assertTrue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify


/**
 * Created by pedro on 14/4/22.
 */
@RunWith(MockitoJUnitRunner::class)
class BitrateManagerTest {

  @Mock
  private lateinit var connectCheckerRtsp: ConnectCheckerRtsp

  @Test
  fun `WHEN set multiple values THEN return total of values each second`() {
    val bitrateManager = BitrateManager(connectCheckerRtsp)
    val fakeValues = arrayOf(100L, 200L, 300L, 400L, 500L)
    var expectedResult = 0L
    fakeValues.forEach {
      bitrateManager.calculateBitrate(it)
      expectedResult += it
    }
    Thread.sleep(1000)
    val value = 100L
    bitrateManager.calculateBitrate(value)
    expectedResult += value
    val resultValue = ArgumentCaptor.forClass(Long::class.java)
    verify(connectCheckerRtsp, times(1)).onNewBitrateRtsp(resultValue.capture())
    val marginError = 20
    assertTrue(expectedResult - marginError <= resultValue.value && resultValue.value <= expectedResult + marginError)
  }
}