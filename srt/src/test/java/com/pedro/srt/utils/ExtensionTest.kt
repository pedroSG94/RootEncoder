package com.pedro.srt.utils

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/11/23.
 */
class ExtensionTest {

  @Test
  fun `remove info`() {
    val buffer = ByteBuffer.wrap(ByteArray(256) { 0x00 }.mapIndexed { index, byte -> index.toByte()  }.toByteArray())
    val info = MediaCodec.BufferInfo()
    val offset = 4
    val minusLimit = 2
    info.presentationTimeUs = 0
    info.offset = offset
    info.size = buffer.remaining() - minusLimit
    info.flags = 0

    val result = buffer.removeInfo(info)
    assertEquals(buffer.capacity() - offset - minusLimit, result.remaining())
    assertEquals(offset.toByte(), result.get(0))
    assertEquals((buffer.capacity() - 1 - minusLimit).toByte(), result.get(result.remaining() - 1))
  }
}