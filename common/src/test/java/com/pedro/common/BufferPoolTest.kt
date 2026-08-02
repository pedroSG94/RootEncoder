package com.pedro.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferPoolTest {

  @Test
  fun `GIVEN a size WHEN acquire THEN return a power of two array able to contain it`() {
    val pool = BufferPool()
    val sizes = listOf(0, 1, 500, 1024, 1025, 4096, 100000)
    sizes.forEach { size ->
      val buffer = pool.acquire(size)
      assertTrue("$size does not fit in ${buffer.size}", buffer.size >= size)
      assertEquals("${buffer.size} is not a power of two", 1, Integer.bitCount(buffer.size))
      assertTrue("${buffer.size} is smaller than the min size class", buffer.size >= 1024)
    }
  }

  @Test
  fun `GIVEN a released buffer WHEN acquire the same size class THEN reuse the same instance`() {
    val pool = BufferPool()
    val buffer = pool.acquire(3000)
    assertEquals(4096, buffer.size)
    pool.release(buffer)
    //any size inside the same class must get the very same array back
    assertSame(buffer, pool.acquire(2049))
  }

  @Test
  fun `GIVEN an empty pool WHEN acquire twice without releasing THEN return different instances`() {
    val pool = BufferPool()
    val first = pool.acquire(1024)
    val second = pool.acquire(1024)
    assertNotSame(first, second)
  }

  @Test
  fun `GIVEN a foreign array WHEN release THEN ignore it`() {
    val pool = BufferPool()
    //not a power of two so it was never created by acquire
    val foreign = ByteArray(3000)
    pool.release(foreign)
    assertEquals(0, pool.getRetainedBytes())
    assertNotSame(foreign, pool.acquire(3000))
  }

  @Test
  fun `GIVEN a size over the max class WHEN acquire and release THEN allocate exact and do not retain`() {
    val pool = BufferPool()
    val size = 8 * 1024 * 1024
    val buffer = pool.acquire(size)
    assertEquals(size, buffer.size)
    pool.release(buffer)
    assertEquals(0, pool.getRetainedBytes())
  }

  @Test
  fun `GIVEN more buffers than the limit WHEN release THEN retain only the allowed ones`() {
    val pool = BufferPool(maxBuffersPerSizeClass = 2)
    val buffers = List(5) { pool.acquire(1024) }
    buffers.forEach { pool.release(it) }
    assertEquals(2048, pool.getRetainedBytes())
  }

  @Test
  fun `GIVEN a retained bytes limit WHEN release THEN stop retaining once reached`() {
    val pool = BufferPool(maxRetainedBytes = 4096)
    val buffers = List(8) { pool.acquire(1024) }
    buffers.forEach { pool.release(it) }
    assertEquals(4096, pool.getRetainedBytes())
  }

  @Test
  fun `GIVEN the same buffer WHEN release twice THEN retain it only once`() {
    val pool = BufferPool()
    val buffer = pool.acquire(1024)
    pool.release(buffer)
    pool.release(buffer)
    assertEquals(1024, pool.getRetainedBytes())
    //the second acquire must not get the very same array than the first one
    assertSame(buffer, pool.acquire(1024))
    assertNotSame(buffer, pool.acquire(1024))
  }

  @Test
  fun `GIVEN retained buffers WHEN clear THEN drop all of them`() {
    val pool = BufferPool()
    val buffer = pool.acquire(1024)
    pool.release(buffer)
    assertEquals(1024, pool.getRetainedBytes())
    pool.clear()
    assertEquals(0, pool.getRetainedBytes())
    assertNotSame(buffer, pool.acquire(1024))
  }

  @Test
  fun `GIVEN a dirty reused buffer WHEN write a shorter frame THEN only the frame bytes are read`() {
    val pool = BufferPool()
    val first = pool.acquire(1024)
    //fill it with garbage the next user must not see
    first.fill(0xFF.toByte())
    pool.release(first)

    val frame = byteArrayOf(1, 2, 3, 4, 5)
    val reused = pool.acquire(frame.size)
    assertSame(first, reused)
    frame.copyInto(reused)
    //a consumer limited to the frame size never observes the stale bytes
    assertArrayEquals(frame, reused.copyOfRange(0, frame.size))
  }
}
