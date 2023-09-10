package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfDateTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf date`() {
    val buffer = byteArrayOf(11, 64, -93, -120, 0, 0, 0, 0, 0, 0, 0)

    val input = ByteArrayInputStream(buffer)
    val amfDate = AmfData.getAmfData(input)

    assertTrue(amfDate is AmfDate)
    assertEquals(2500.0, (amfDate as AmfDate).date, 0.0)
  }

  @Test
  fun `GIVEN a amf date WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(11, 64, -93, -120, 0, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val amfDate = AmfDate(2500.0)
    amfDate.writeHeader(output)
    amfDate.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}