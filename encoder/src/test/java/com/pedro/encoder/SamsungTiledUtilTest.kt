package com.pedro.encoder

import com.pedro.encoder.utils.yuv.SamsungTiledUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Tiles are 64x32 so the smallest frame that shows the 4x2 group of the layout is 256x64, too big
 * to write the frame as a literal. The frames are filled with a pattern where each byte says where
 * it comes from (its tile, its row or its column) and the tests read back single bytes of the
 * result, so the expected values stay literal.
 *
 * A 256x64 I420 frame is 24576 bytes:
 * luma 0..16383, U 16384..20479, V 20480..24575
 *
 * and its tiled version is also 24576 bytes:
 * luma 8 tiles of 2048 at 0, chroma 4 tiles of 2048 at 16384
 */
class SamsungTiledUtilTest {

  private val width = 256
  private val height = 64
  private val lumaSize = width * height
  private val chromaSize = lumaSize / 4

  /** Frame where every luma byte is the index of the tile it belongs to in raster order, 0 to 7. */
  private fun frameOfTileIndex() = ByteArray(lumaSize + chromaSize * 2) { i ->
    if (i >= lumaSize) 0 else ((i / width / 32) * 4 + (i % width / 64)).toByte()
  }

  /** Frame where every luma byte is its own row, 0 to 63. */
  private fun frameOfRow() = ByteArray(lumaSize + chromaSize * 2) { i ->
    if (i >= lumaSize) 0 else (i / width).toByte()
  }

  /** Frame where every luma byte is its own column divided by 4, 0 to 63. */
  private fun frameOfColumn() = ByteArray(lumaSize + chromaSize * 2) { i ->
    if (i >= lumaSize) 0 else (i % width / 4).toByte()
  }

  /** Frame where U is its own column, 0 to 127, and V that column plus 128. */
  private fun frameOfChromaColumn() = ByteArray(lumaSize + chromaSize * 2) { i ->
    when {
      i < lumaSize -> 0
      i < lumaSize + chromaSize -> ((i - lumaSize) % 128).toByte()
      else -> ((i - lumaSize - chromaSize) % 128 + 128).toByte()
    }
  }

  private fun tiled(frame: ByteArray): ByteBuffer {
    val buffer = ByteBuffer.allocate(SamsungTiledUtil.getSize(width, height))
    val filled = SamsungTiledUtil.copy(buffer, frame, 0, frame.size, width, height, false)
    assertEquals(buffer.capacity(), filled)
    return buffer
  }

  private fun bytesAt(buffer: ByteBuffer, offset: Int, count: Int) =
    ByteArray(count) { buffer.get(offset + it) }

  @Test
  fun `calculate tiled size`() {
    //640x480: luma 150 tiles aligned to 8KB (311296) plus chroma 80 tiles (163840).
    //This is the size OMX.SEC.avc.enc reads and the crash reported in the issue 2158
    assertEquals(475136, SamsungTiledUtil.getSize(640, 480))
    assertEquals(1433600, SamsungTiledUtil.getSize(1280, 720))
    assertEquals(24576, SamsungTiledUtil.getSize(256, 64))
  }

  @Test
  fun `only resolutions with an even number of tiles per row are supported`() {
    assertTrue(SamsungTiledUtil.isSupported(640, 480))
    assertTrue(SamsungTiledUtil.isSupported(1280, 720))
    //320 and 352 are 5 and 6 tiles per row counting the incomplete one, the pairs don't fit
    assertFalse(SamsungTiledUtil.isSupported(320, 240))
    assertFalse(SamsungTiledUtil.isSupported(352, 288))
  }

  @Test
  fun `tiles are written in the 4x2 group order`() {
    val buffer = tiled(frameOfTileIndex())
    //first byte of each of the 8 tiles written, it tells which raster tile landed there
    val order = ByteArray(8) { buffer.get(it * 2048) }
    //pairs of tiles zig zag between both tile rows: (0,0)(1,0) (0,1)(1,1) (2,1)(3,1) (2,0)(3,0)
    assertArrayEquals(byteArrayOf(0, 1, 4, 5, 6, 7, 2, 3), order)
  }

  @Test
  fun `rows inside a tile keep their order`() {
    val buffer = tiled(frameOfRow())
    //tile 0 holds the raster tile (0,0), so its 32 rows of 64 bytes are the frame rows 0 to 31
    val first = ByteArray(32) { buffer.get(it * 64) }
    assertArrayEquals(
      byteArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
      ), first
    )
    //tile 2 holds the raster tile (0,1), the frame rows 32 to 63
    val second = ByteArray(32) { buffer.get(2 * 2048 + it * 64) }
    assertArrayEquals(
      byteArrayOf(
        32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
        48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63
      ), second
    )
  }

  @Test
  fun `columns inside a tile keep their order`() {
    val buffer = tiled(frameOfColumn())
    //tile 0 holds the raster tile (0,0), its first row are the frame columns 0 to 63
    assertArrayEquals(byteArrayOf(0, 0, 0, 0, 1, 1, 1, 1), bytesAt(buffer, 0, 8))
    //tile 6 holds the raster tile (2,0), the frame columns 128 to 191
    assertArrayEquals(byteArrayOf(32, 32, 32, 32, 33, 33, 33, 33), bytesAt(buffer, 6 * 2048, 8))
  }

  @Test
  fun `chroma is written interleaved after the luma aligned to 8KB`() {
    val buffer = tiled(frameOfChromaColumn())
    //the chroma plane starts at 16384, the luma size already aligned. Its first tile is the
    //chroma columns 0 to 31 of both planes interleaved as U V U V
    assertArrayEquals(
      byteArrayOf(0, -128, 1, -127, 2, -126, 3, -125), bytesAt(buffer, 16384, 8)
    )
    //the chroma grid is a single tile row so its tiles are in raster order, the third one holds
    //the chroma columns 64 to 95
    assertArrayEquals(
      byteArrayOf(64, -64, 65, -63, 66, -62, 67, -61), bytesAt(buffer, 16384 + 2 * 2048, 8)
    )
  }

  @Test
  fun `copy fail without modify the buffer`() {
    val frame = frameOfRow()
    val size = SamsungTiledUtil.getSize(width, height)

    val small = ByteBuffer.allocate(size - 1)
    assertEquals(-1, SamsungTiledUtil.copy(small, frame, 0, frame.size, width, height, false))
    assertEquals(0, small.position())

    val buffer = ByteBuffer.allocate(size)
    assertEquals(-1, SamsungTiledUtil.copy(buffer, frame, 0, frame.size - 1, width, height, false))
    assertEquals(0, buffer.position())
  }
}
