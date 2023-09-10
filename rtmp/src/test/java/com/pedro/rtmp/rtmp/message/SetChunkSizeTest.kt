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
class SetChunkSizeTest {

  private val commandSessionHistory = CommandSessionHistory()

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected set chunk size packet`() {
    val buffer = byteArrayOf(2, 0, 0, 0, 0, 0, 4, 1, 0, 0, 0, 0, 0, 0, 1, 0)
    val setChunkSize = SetChunkSize(256)

    val message = RtmpMessage.getRtmpMessage(ByteArrayInputStream(buffer), RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

    assertTrue(message is SetChunkSize)
    assertEquals(setChunkSize.toString(), (message as SetChunkSize).toString())
  }

  @Test
  fun `GIVEN a set chunk size packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(2, 0, 0, 0, 0, 0, 4, 1, 0, 0, 0, 0, 0, 0, 1, 0)
    val output = ByteArrayOutputStream()

    val setChunkSize = SetChunkSize(256)
    setChunkSize.writeHeader(output)
    setChunkSize.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}