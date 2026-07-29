package com.pedro.common.base

import com.pedro.common.ConnectChecker
import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito
import java.nio.ByteBuffer

/**
 * The sender copies each frame into a pooled array bigger than the frame itself. These tests lock
 * the shape of the resulting buffer so the packetizers keep behaving like they did with a copy.
 */
class BaseSenderTest {

  private class FakeSender: BaseSender(Mockito.mock(ConnectChecker::class.java), "FakeSender") {
    override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {}
    override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {}
    override suspend fun onRun() {}
    override suspend fun stopImp(clear: Boolean) {}

    //consumeFrame is the only way senders read frames, use it to inspect what was enqueued
    suspend fun consumeOne(block: (MediaFrame) -> Unit) = consumeFrame { block(it) }
    fun forceRunning() { running = true }
  }

  private fun ByteBuffer.readAll(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }

  @Test
  fun `GIVEN a frame WHEN send THEN the buffer capacity is the frame size not the pooled size`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    val frame = byteArrayOf(0, 0, 0, 1, 101, 1, 2, 3, 4, 5)

    sender.sendMediaFrame(ByteBuffer.wrap(frame), MediaFrame.Info(0, frame.size, 0, true), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      val data = mediaFrame.data
      //the pooled array is 1024 bytes, nothing beyond the frame may be reachable
      assertEquals(frame.size, data.capacity())
      assertEquals(0, data.position())
      assertEquals(frame.size, data.limit())
      assertArrayEquals(frame, data.readAll())
    }
  }

  @Test
  fun `GIVEN a frame WHEN removeInfo THEN return the same bytes than the source`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    val frame = byteArrayOf(0, 0, 0, 1, 101, 9, 8, 7)

    sender.sendMediaFrame(ByteBuffer.wrap(frame), MediaFrame.Info(0, frame.size, 0, true), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
      assertArrayEquals(frame, fixedBuffer.readAll())
    }
  }

  @Test
  fun `GIVEN a buffer with offset WHEN removeInfo THEN the frame is not shifted twice`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    val prefix = byteArrayOf(0x55, 0x55, 0x55, 0x55)
    val frame = byteArrayOf(0, 0, 0, 1, 101, 9, 8, 7)
    //MediaCodec hands the buffer positioned at the offset reported in the info
    val buffer = ByteBuffer.wrap(prefix.plus(frame))
    buffer.position(prefix.size)

    sender.sendMediaFrame(buffer, MediaFrame.Info(prefix.size, frame.size, 0, true), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      //the copy keeps the absolute offsets, so the whole buffer is still there
      assertEquals(prefix.size + frame.size, mediaFrame.data.capacity())
      assertEquals(0, mediaFrame.data.position())
      //removeInfo skips the prefix once, not twice
      val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
      assertArrayEquals(frame, fixedBuffer.readAll())
    }
  }

  @Test
  fun `GIVEN a buffer with offset WHEN send THEN produce the same bytes than a plain copy`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    val source = byteArrayOf(0x55, 0x55, 0x55, 0x55, 0, 0, 0, 1, 101, 9, 8, 7)
    val buffer = ByteBuffer.wrap(source)
    buffer.position(4)

    sender.sendMediaFrame(buffer, MediaFrame.Info(4, source.size - 4, 0, true), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      //the old path was ByteBuffer.wrap(toByteArray()), copying from absolute 0
      val expected = ByteBuffer.wrap(source.copyOf())
      val pooled = mediaFrame.data
      assertEquals(expected.capacity(), pooled.capacity())
      assertEquals(expected.position(), pooled.position())
      assertEquals(expected.limit(), pooled.limit())
      assertArrayEquals(source, pooled.readAll())
    }
  }

  @Test
  fun `GIVEN a recycled dirty buffer WHEN send a smaller frame THEN stale bytes are unreachable`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    //first frame fills a 1024 pooled array with a byte we must never see again
    val big = ByteArray(600) { 0xFF.toByte() }
    sender.sendMediaFrame(ByteBuffer.wrap(big), MediaFrame.Info(0, big.size, 0, true), MediaFrame.Type.VIDEO)
    var firstArray: ByteArray? = null
    //consuming it recycles the pooled array
    sender.consumeOne { firstArray = it.data.array() }

    val small = byteArrayOf(1, 2, 3)
    sender.sendMediaFrame(ByteBuffer.wrap(small), MediaFrame.Info(0, small.size, 0, false), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      //same underlying array reused, but capped to the new frame
      assertSame(firstArray, mediaFrame.data.array())
      assertEquals(small.size, mediaFrame.data.capacity())
      assertArrayEquals(small, mediaFrame.data.readAll())
    }
  }

  @Test
  fun `GIVEN an info bigger than the frame WHEN removeInfo THEN do not expose stale bytes`() = runBlocking {
    val sender = FakeSender()
    sender.forceRunning()
    val big = ByteArray(600) { 0xFF.toByte() }
    sender.sendMediaFrame(ByteBuffer.wrap(big), MediaFrame.Info(0, big.size, 0, true), MediaFrame.Type.VIDEO)
    sender.consumeOne { }

    val small = byteArrayOf(1, 2, 3)
    //a malformed info asking for more bytes than the frame has
    sender.sendMediaFrame(ByteBuffer.wrap(small), MediaFrame.Info(0, 500, 0, false), MediaFrame.Type.VIDEO)

    sender.consumeOne { mediaFrame ->
      val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
      //truncated to the real frame instead of reading into the previous one
      assertEquals(small.size, fixedBuffer.remaining())
      assertArrayEquals(small, fixedBuffer.readAll())
    }
  }
}
