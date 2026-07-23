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

package com.pedro.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify


/**
 * Created by pedro on 9/9/23.
 */
@RunWith(MockitoJUnitRunner::class)
class BitrateManagerTest {

  @OptIn(ExperimentalCoroutinesApi::class)
  @get:Rule
  val mainDispatcherRule = object: TestWatcher() {
    override fun starting(description: Description) {
      Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
      Dispatchers.resetMain()
    }
  }
  @Mock
  private lateinit var connectChecker: ConnectChecker
  private lateinit var timeUtilsMocked: MockedStatic<TimeUtils>
  private var fakeTime = 7502849023L

  @Before
  fun setup() {
    timeUtilsMocked = Mockito.mockStatic(TimeUtils::class.java)
    timeUtilsMocked.`when`<Long>(TimeUtils::getCurrentTimeMillis).then { fakeTime }
  }

  @After
  fun teardown() {
    timeUtilsMocked.close()
  }

  @Test
  fun `WHEN set multiple values THEN return total of values each second`() = runTest {
    val bitrateManager = BitrateManager(connectChecker)
    val fakeValues = arrayOf(100L, 200L, 300L, 400L, 500L)
    var expectedResult = 0L
    fakeValues.forEach {
      bitrateManager.calculateBitrate(it)
      expectedResult += it
    }
    fakeTime += 1000
    val value = 100L
    bitrateManager.calculateBitrate(value)
    expectedResult += value
    val resultValue = argumentCaptor<Long>()
    verify(connectChecker, times(1)).onNewBitrate(resultValue.capture())
    val marginError = 20
    assertTrue(expectedResult - marginError <= resultValue.firstValue && resultValue.firstValue <= expectedResult + marginError)
  }
}
