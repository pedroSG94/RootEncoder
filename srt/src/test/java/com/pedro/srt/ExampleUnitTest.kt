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

import com.pedro.srt.srt.packets.control.Handshake
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    val expected = byteArrayOf(
      0x80.toByte(),0x00,0x00,0x00, 0x00,0x00,0x00,0x00,
      0x00,0x00,0x00,0x63, 0x00,0x00,0x00,0x00,
      0x00,0x00,0x00,0x04, 0x00,0x00,0x00,0x02,
      0x62,0x2f,0xba.toByte(),0xb9.toByte(), 0x00,0x00,0x05,0xdc.toByte(),
      0x00,0x00,0x20,0x00, 0x00,0x00,0x00,0x01,
      0x28,0x79,0x34,0x53, 0x00,0x00,0x00,0x00,

      0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x00,
      0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x00
      //0x01,0x00,0x00,0x7f, 0x00,0x00,0x00,0x00,
      //0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x00
    )
    val hs = Handshake()
    hs.write(99, 0)
    val bytes = hs.getData()
    println(bytes.map { "%02x".format(it) }.toString())
    assertArrayEquals(expected, bytes)
  }
}