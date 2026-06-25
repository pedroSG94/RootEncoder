package com.pedro.common

import com.pedro.common.nal.NalReader
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class NalReaderTest {

  private val header = byteArrayOf(
    0x00, 0x00, 0x01
  )
  private val nal = header.plus(ByteArray(10_000) { 0x0f })

  @Test
  fun testReadMultipleNalsFromBuffer() {
    val buffer = ByteBuffer.wrap(nal.plus(nal).plus(nal))
    val nals = NalReader.extractNals(buffer, VideoCodec.H264, true)
    assertEquals(3, nals.size)
    nals.forEach {
      assertEquals(10_000, it.capacity())
    }
  }

  @Test
  fun testReadSingleNalFromBuffer() {
    val buffer = ByteBuffer.wrap(nal)
    val nals = NalReader.extractNals(buffer, VideoCodec.H264, true)
    assertEquals(1, nals.size)
    nals.forEach {
      assertEquals(10_000, it.capacity())
    }
  }

  @Test
  fun testRemoveSeiAudSpsPpsNalsFromBuffer() {
    val sei = header.plus(0x06).plus(ByteArray(10) { 0x0f })
    val aud = header.plus(0x09).plus(ByteArray(10) { 0x0f })
    val sps = header.plus(0x07).plus(ByteArray(10) { 0x0f })
    val pps = header.plus(0x08).plus(ByteArray(10) { 0x0f })
    val buffer = ByteBuffer.wrap(sei.plus(aud).plus(sps).plus(pps).plus(nal))
    val nals = NalReader.extractNals(buffer, VideoCodec.H264, true)
    assertEquals(1, nals.size)
    nals.forEach {
      assertEquals(10_000, it.capacity())
    }
  }
}