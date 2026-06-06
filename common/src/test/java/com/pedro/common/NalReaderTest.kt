package com.pedro.common

import com.pedro.common.nal.NalReader
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class NalReaderTest {

  private val header = byteArrayOf(
    0x00, 0x00, 0x01
  )
  private val nal = header.plus(ByteArray(10_000) { 0x08 })

  @Test
  fun testReadMultipleNalsFromBuffer() {
    val buffer = ByteBuffer.wrap(nal.plus(nal).plus(nal))
    val nals = NalReader.extractNals(buffer)
    assertEquals(3, nals.size)
    nals.forEach {
      assertEquals(10_000, it.capacity())
    }
  }

  @Test
  fun testReadSingleNalFromBuffer() {
    val buffer = ByteBuffer.wrap(nal)
    val nals = NalReader.extractNals(buffer)
    assertEquals(1, nals.size)
    nals.forEach {
      assertEquals(10_000, it.capacity())
    }
  }
}