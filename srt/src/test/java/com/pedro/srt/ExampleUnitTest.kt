/*
 * Copyright (C) 2023 pedroSG94.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.pedro.srt

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 *
 */
class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    val v1 = (0.toInt() shl 6) or (1.toInt() and 0xF) or (0b11 shl 4)
    val v2 = ((0.toInt() and 0x3 shl 6) or (0b11 and 0x3 shl 4) or (1 and 0xF))
    assertEquals(v1, v2)
  }
}