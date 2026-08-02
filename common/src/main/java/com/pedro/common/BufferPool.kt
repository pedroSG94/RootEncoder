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

/**
 * Created by pedro on 29/7/26.
 *
 * Pool of reusable ByteArray to avoid allocating one per encoded frame.
 *
 * An encoded frame must be copied out of the MediaCodec output buffer before giving it back to
 * the codec, so the copy is unavoidable, but the allocation is not. At 6Mbps a stream allocates
 * around 750KB/s of short lived arrays, and all of it ends in the GC.
 *
 * Buffers are grouped in power of two size classes so a frame gets the smallest available array
 * able to contain it. Arrays are handed out dirty (never zeroed) because the caller always
 * writes the whole frame and reads it back limited to the frame size.
 *
 * Thread safe: acquired from the encoder thread and released from the sender thread.
 */
class BufferPool(
  private val maxBuffersPerSizeClass: Int = 8,
  private val maxRetainedBytes: Long = 16 * 1024 * 1024
) {

  companion object {
    //1KB, audio frames are smaller but a smaller class is not worth the fragmentation
    private const val MIN_SIZE_CLASS = 10
    //4MB, bigger frames are allocated and discarded as before
    private const val MAX_SIZE_CLASS = 22
  }

  private val buckets = arrayOfNulls<ArrayDeque<ByteArray>>(MAX_SIZE_CLASS - MIN_SIZE_CLASS + 1)
  private val lock = Any()
  private var retainedBytes = 0L

  /**
   * Return an array of at least [minSize] bytes. The content is undefined.
   */
  fun acquire(minSize: Int): ByteArray {
    val sizeClass = sizeClassOf(minSize)
    if (sizeClass > MAX_SIZE_CLASS) return ByteArray(minSize)
    synchronized(lock) {
      val buffer = buckets[sizeClass - MIN_SIZE_CLASS]?.removeLastOrNull()
      if (buffer != null) {
        retainedBytes -= buffer.size
        return buffer
      }
    }
    return ByteArray(1 shl sizeClass)
  }

  fun release(buffer: java.nio.ByteBuffer) {
    if (buffer.hasArray()) release(buffer.array())
  }

  /**
   * Give an array back to the pool. Arrays not created by [acquire], and arrays already released,
   * are ignored, so the same array can never be handed out to two users at once.
   */
  fun release(buffer: ByteArray) {
    val size = buffer.size
    if (Integer.bitCount(size) != 1) return
    val sizeClass = 31 - Integer.numberOfLeadingZeros(size)
    if (sizeClass !in MIN_SIZE_CLASS..MAX_SIZE_CLASS) return
    synchronized(lock) {
      if (retainedBytes + size > maxRetainedBytes) return
      val bucket = buckets[sizeClass - MIN_SIZE_CLASS]
        ?: ArrayDeque<ByteArray>().also { buckets[sizeClass - MIN_SIZE_CLASS] = it }
      if (bucket.size >= maxBuffersPerSizeClass) return
      //a double release would put the same array twice in the bucket and corrupt one of its users
      if (bucket.any { it === buffer }) return
      bucket.addLast(buffer)
      retainedBytes += size
    }
  }

  fun clear() {
    synchronized(lock) {
      buckets.fill(null)
      retainedBytes = 0
    }
  }

  fun getRetainedBytes(): Long = synchronized(lock) { retainedBytes }

  private fun sizeClassOf(size: Int): Int {
    val value = maxOf(size, 1 shl MIN_SIZE_CLASS)
    return 32 - Integer.numberOfLeadingZeros(value - 1)
  }
}
