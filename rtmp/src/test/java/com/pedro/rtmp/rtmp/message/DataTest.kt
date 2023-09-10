package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import com.pedro.rtmp.utils.CommandSessionHistory
import com.pedro.rtmp.utils.RtmpConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class DataTest {

  private val commandSessionHistory = CommandSessionHistory()

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected data amf0 packet`() {
    val buffer = byteArrayOf(3, 0, 0, 0, 0, 0, 25, 18, 0, 0, 0, 0, 2, 0, 4, 116, 101, 115, 116, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)
    val dataAmf0 = DataAmf0("test")
    dataAmf0.addData(AmfString("random"))
    dataAmf0.addData(AmfNumber(20.0))

    val message = RtmpMessage.getRtmpMessage(ByteArrayInputStream(buffer), RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

    assertTrue(message is DataAmf0)
    assertEquals(dataAmf0.toString(), (message as DataAmf0).toString())
  }

  @Test
  fun `GIVEN a data amf0 packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(3, 0, 0, 0, 0, 0, 25, 18, 0, 0, 0, 0, 2, 0, 4, 116, 101, 115, 116, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)
    val output = ByteArrayOutputStream()

    val dataAmf0 = DataAmf0("test")
    dataAmf0.addData(AmfString("random"))
    dataAmf0.addData(AmfNumber(20.0))

    dataAmf0.writeHeader(output)
    dataAmf0.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}