package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfStrictArray
import com.pedro.rtmp.amf.v0.AmfString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfStrictArrayTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf strict array`() {
    val buffer = byteArrayOf(10, 0, 0, 0, 2, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)
    val list = mutableListOf<AmfData>()
    list.add(AmfString("random"))
    list.add(AmfNumber(20.0))

    val input = ByteArrayInputStream(buffer)
    val amfStrictArray = AmfData.getAmfData(input)

    assertTrue(amfStrictArray is AmfStrictArray)

    val strictArray = amfStrictArray as AmfStrictArray
    list.forEachIndexed { index, amfData ->
      val value = strictArray.items[index]
      if (value is AmfString && amfData is AmfString) {
        assertEquals(amfData.value, value.value)
      } else if (value is AmfNumber && amfData is AmfNumber) {
        assertEquals(amfData.value, value.value, 0.0)
      } else {
        assertTrue(false)
      }
    }
  }

  @Test
  fun `GIVEN a amf strict array WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(10, 0, 0, 0, 2, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val list = mutableListOf<AmfData>()
    list.add(AmfString("random"))
    list.add(AmfNumber(20.0))
    val amfStrictArray = AmfStrictArray(list)
    amfStrictArray.writeHeader(output)
    amfStrictArray.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}