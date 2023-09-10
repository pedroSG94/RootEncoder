package com.pedro.rtmp.rtmp.message

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
class AcknowledgementTest {

  private val commandSessionHistory = CommandSessionHistory()

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected acknowledgement packet`() {
    val buffer = byteArrayOf(2, 0, 0, 0, 0, 0, 4, 3, 0, 0, 0, 0, 0, 0, 0, 5)
    val acknowledgement = Acknowledgement(5)

    val message = RtmpMessage.getRtmpMessage(ByteArrayInputStream(buffer), RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

    assertTrue(message is Acknowledgement)
    assertEquals(acknowledgement.toString(), (message as Acknowledgement).toString())
  }

  @Test
  fun `GIVEN an acknowledgement packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(2, 0, 0, 0, 0, 0, 4, 3, 0, 0, 0, 0, 0, 0, 0, 5)
    val output = ByteArrayOutputStream()

    val acknowledgement = Acknowledgement(5)
    acknowledgement.writeHeader(output)
    acknowledgement.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}