package com.pedro.encoder

import com.pedro.encoder.utils.yuv.YUVUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class YUVUtilTest {

  //4x2 I420 frame -> Y = 8 bytes, U = 2 bytes, V = 2 bytes
  private val i420 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
  //4x2 NV12 frame -> Y = 8 bytes, UV = 4 bytes
  private val nv12 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)

  @Test
  fun `copy planar frame aligned`() {
    val byteBuffer = ByteBuffer.allocate(32)
    val size = YUVUtil.copyAlignedPlanes(byteBuffer, i420, 0, i420.size, 4, 2, false, 8)
    assertEquals(24, size)
    val expected = byteArrayOf(
      1, 2, 3, 4, 5, 6, 7, 8,
      9, 10, 0, 0, 0, 0, 0, 0,
      11, 12, 0, 0, 0, 0, 0, 0
    )
    assertArrayEquals(expected, byteBuffer.array().copyOf(size))
    assertEquals(size, byteBuffer.position())
  }

  @Test
  fun `copy semi planar frame aligned`() {
    val byteBuffer = ByteBuffer.allocate(32)
    val size = YUVUtil.copyAlignedPlanes(byteBuffer, nv12, 0, nv12.size, 4, 2, true, 8)
    assertEquals(16, size)
    val expected = byteArrayOf(
      1, 2, 3, 4, 5, 6, 7, 8,
      9, 10, 11, 12, 0, 0, 0, 0
    )
    assertArrayEquals(expected, byteBuffer.array().copyOf(size))
  }

  @Test
  fun `copy planar frame aligned using an offset`() {
    val byteBuffer = ByteBuffer.allocate(32)
    val buffer = byteArrayOf(0, 0) + i420
    val size = YUVUtil.copyAlignedPlanes(byteBuffer, buffer, 2, i420.size, 4, 2, false, 8)
    assertEquals(24, size)
    val expected = byteArrayOf(
      1, 2, 3, 4, 5, 6, 7, 8,
      9, 10, 0, 0, 0, 0, 0, 0,
      11, 12, 0, 0, 0, 0, 0, 0
    )
    assertArrayEquals(expected, byteBuffer.array().copyOf(size))
  }

  @Test
  fun `copy fail without modify the buffer`() {
    //buffer too small
    val smallBuffer = ByteBuffer.allocate(16)
    assertEquals(-1, YUVUtil.copyAlignedPlanes(smallBuffer, i420, 0, i420.size, 4, 2, false, 8))
    assertEquals(0, smallBuffer.position())
    //frame too small
    val byteBuffer = ByteBuffer.allocate(32)
    assertEquals(-1, YUVUtil.copyAlignedPlanes(byteBuffer, i420, 0, i420.size - 1, 4, 2, false, 8))
    assertEquals(0, byteBuffer.position())
  }

  @Test
  fun `calculate aligned size`() {
    //640x480 planar aligned to 8KB. Size read by the OMX.SEC encoders instead of 640 * 480 * 3 / 2
    assertEquals(475136, YUVUtil.getAlignedPlanesSize(640, 480, false, 8192))
    assertEquals(466944, YUVUtil.getAlignedPlanesSize(640, 480, true, 8192))
    //resolution where all the planes are already aligned, no padding added
    assertEquals(49152, YUVUtil.getAlignedPlanesSize(256, 128, false, 8192))
    //no alignment, packed size
    assertEquals(460800, YUVUtil.getAlignedPlanesSize(640, 480, false, 1))
  }
}
