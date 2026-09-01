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

package com.pedro.library.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * maxBitrate is 3200000 of video plus 64000 of audio, the configuration reported in issue #2177.
 * adaptBitrate only produces a value every 5 samples.
 */
class BitrateAdapterTest {

  private val maxBitrate = 3264000

  private fun adapt(samples: List<Long>): List<Int> {
    val results = mutableListOf<Int>()
    val adapter = BitrateAdapter { results.add(it) }
    adapter.setMaxBitrate(maxBitrate)
    samples.forEach { adapter.adaptBitrate(it) }
    return results
  }

  private fun adapt(samples: List<Long>, hasCongestion: Boolean): List<Int> {
    val results = mutableListOf<Int>()
    val adapter = BitrateAdapter { results.add(it) }
    adapter.setMaxBitrate(maxBitrate)
    samples.forEach { adapter.adaptBitrate(it, hasCongestion) }
    return results
  }

  @Test
  fun `GIVEN a link faster than max WHEN adapt THEN keep max bitrate`() {
    val results = adapt(List(5) { 3400000L })
    assertEquals(listOf(3264000), results)
  }

  @Test
  fun `GIVEN a link a bit slower than max WHEN adapt THEN go below the link instead of pinning at max`() {
    //3150000 is fast enough to stay above oldBitrate * 0.9, so the decrease branch was never
    //taken and the bitrate stayed pinned at maxBitrate over a link that cannot carry it
    val results = adapt(List(5) { 3150000L })
    assertEquals(listOf(2441249), results)
    assertTrue(results.first() < 3150000)
  }

  @Test
  fun `GIVEN a link a bit slower than max WHEN adapt many times THEN never settle above the link`() {
    val link = 3150000L
    val results = mutableListOf<Int>()
    val adapter = BitrateAdapter { results.add(it) }
    adapter.setMaxBitrate(maxBitrate)
    var configured = maxBitrate
    repeat(10 * 5) {
      //the sender can only push what the link carries
      adapter.adaptBitrate(minOf(configured.toLong(), link))
      configured = results.lastOrNull() ?: configured
    }
    assertEquals(10, results.size)
    assertTrue("settled above the link: $results", results.count { it > link } < results.size)
  }

  @Test
  fun `GIVEN congestion WHEN measured bitrate reaches max THEN reduce anyway`() {
    val results = adapt(List(5) { 4000000L }, hasCongestion = true)
    assertEquals(listOf(3100000), results)
  }

  @Test
  fun `GIVEN no congestion WHEN measured bitrate reaches max THEN keep max bitrate`() {
    val results = adapt(List(5) { 4000000L }, hasCongestion = false)
    assertEquals(listOf(3264000), results)
  }
}
