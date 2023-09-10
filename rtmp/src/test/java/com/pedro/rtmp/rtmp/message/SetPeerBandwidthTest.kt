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
class SetPeerBandwidthTest {

  private val commandSessionHistory = CommandSessionHistory()

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected set peer bandwidth packet`() {
    val buffer = byteArrayOf(2, 0, 0, 0, 0, 0, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 2)
    val setPeerBandwidth = SetPeerBandwidth()

    val message = RtmpMessage.getRtmpMessage(ByteArrayInputStream(buffer), RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

    assertTrue(message is SetPeerBandwidth)
    assertEquals(setPeerBandwidth.toString(), (message as SetPeerBandwidth).toString())
  }

  @Test
  fun `GIVEN a set peer bandwidth packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(2, 0, 0, 0, 0, 0, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 2)
    val output = ByteArrayOutputStream()

    val setPeerBandwidth = SetPeerBandwidth()
    setPeerBandwidth.writeHeader(output)
    setPeerBandwidth.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}