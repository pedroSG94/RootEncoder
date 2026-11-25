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

package com.pedro.library.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ForceRendererTest {

  @Test
  fun `GIVEN force render enabled WHEN start THEN callback is called each tick`() {
    val forceRenderer = ForceRenderer()
    forceRenderer.setEnabled(true, 100)
    val ticks = CountDownLatch(3)
    forceRenderer.start { ticks.countDown() }

    assertTrue(ticks.await(1, TimeUnit.SECONDS))
    forceRenderer.stop()
  }

  @Test
  fun `GIVEN force render started twice WHEN stop THEN no loop keeps rendering`() {
    val forceRenderer = ForceRenderer()
    //a tick each 100 ms, stop is called long before the first one
    forceRenderer.setEnabled(true, 10)
    val first = AtomicInteger(0)
    val second = AtomicInteger(0)
    forceRenderer.start { first.incrementAndGet() }
    forceRenderer.start { second.incrementAndGet() }
    forceRenderer.stop()
    Thread.sleep(300)

    assertEquals(0, first.get())
    assertEquals(0, second.get())
  }

  @Test
  fun `GIVEN force render stopped WHEN start again THEN callback is called again`() {
    val forceRenderer = ForceRenderer()
    forceRenderer.setEnabled(true, 100)
    forceRenderer.start {}
    forceRenderer.stop()
    val ticks = CountDownLatch(3)
    forceRenderer.start { ticks.countDown() }

    assertTrue(ticks.await(1, TimeUnit.SECONDS))
    forceRenderer.stop()
  }
}
