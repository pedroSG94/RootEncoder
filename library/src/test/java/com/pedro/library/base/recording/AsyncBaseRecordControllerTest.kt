package com.pedro.library.base.recording

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.common.frame.MediaFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The controller copies each frame into a pooled buffer before handing it to the muxer. These
 * tests lock the shape of that buffer and that it goes back to the pool once written.
 */
class AsyncBaseRecordControllerTest {

  private class FakeRecordController: AsyncBaseRecordController() {
    val written = mutableListOf<Pair<ByteArray, Int>>()
    var latch = CountDownLatch(1)

    override fun startRecordImp(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) {}
    override fun startRecordImp(path: String, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) {}
    override fun stopRecordImp() {}
    override fun setVideoFormat(videoFormat: MediaFormat) {}
    override fun setAudioFormat(audioFormat: MediaFormat) {}
    override fun resetFormats() {}

    override suspend fun onWriteFrame(frame: MediaFrame) {
      //keep the array identity and the capacity, the buffer itself is recycled right after
      written.add(frame.data.array() to frame.data.capacity())
    }

    override fun onFrameRecycled() {
      latch.countDown()
    }

    fun start() {
      recordStatus = RecordController.Status.RECORDING
      startRecord("fake", null, RecordController.RecordTracks.ALL)
      recordStatus = RecordController.Status.RECORDING
    }

    fun awaitFrames(count: Int) {
      assertTrue(latch.await(1000, TimeUnit.MILLISECONDS))
      latch = CountDownLatch(count)
    }
  }

  private fun bufferInfoOf(size: Int) = MediaCodec.BufferInfo().apply {
    offset = 0
    this.size = size
    presentationTimeUs = 0
    flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
  }

  @Test
  fun `GIVEN a frame WHEN record THEN the muxer gets a buffer capped to the frame size`() {
    val controller = FakeRecordController()
    controller.latch = CountDownLatch(1)
    controller.start()

    val frame = ByteArray(300) { it.toByte() }
    controller.recordVideo(ByteBuffer.wrap(frame), bufferInfoOf(frame.size))
    controller.awaitFrames(1)
    controller.stopRecord()

    assertEquals(1, controller.written.size)
    //the pooled array is 1024 bytes, the muxer must only reach the frame
    assertEquals(frame.size, controller.written[0].second)
  }

  @Test
  fun `GIVEN two frames WHEN record THEN the second reuses the array of the first`() {
    val controller = FakeRecordController()
    controller.latch = CountDownLatch(1)
    controller.start()

    val first = ByteArray(600) { 0xFF.toByte() }
    controller.recordVideo(ByteBuffer.wrap(first), bufferInfoOf(first.size))
    controller.awaitFrames(1)

    val second = ByteArray(300) { it.toByte() }
    controller.recordVideo(ByteBuffer.wrap(second), bufferInfoOf(second.size))
    controller.awaitFrames(1)
    controller.stopRecord()

    assertEquals(2, controller.written.size)
    //writing the first one recycled its array, so the second must get the very same one
    assertSame(controller.written[0].first, controller.written[1].first)
    //but capped to its own size, the 0xFF left behind are unreachable
    assertEquals(second.size, controller.written[1].second)
  }

  @Test
  fun `GIVEN a frame WHEN record THEN the muxer receives the same bytes`() {
    val controller = FakeRecordController()
    controller.latch = CountDownLatch(1)
    controller.start()

    val frame = byteArrayOf(0, 0, 0, 1, 101, 9, 8, 7)
    controller.recordVideo(ByteBuffer.wrap(frame), bufferInfoOf(frame.size))
    controller.awaitFrames(1)
    controller.stopRecord()

    val (array, capacity) = controller.written[0]
    assertArrayEquals(frame, array.copyOfRange(0, capacity))
  }
}
