package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.FakeRtmpSocket
import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.amf.AmfNumber
import com.pedro.rtmp.rtmp.message.shared.SharedObject
import com.pedro.rtmp.rtmp.message.shared.SharedObjectEvent
import com.pedro.rtmp.rtmp.message.shared.SharedObjectEventType
import com.pedro.rtmp.utils.CommandSessionHistory
import com.pedro.rtmp.utils.RtmpConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class SharedObjectTest {

  private val commandSessionHistory = CommandSessionHistory()
  private lateinit var socket: FakeRtmpSocket

  @Before
  fun setup() {
    socket = FakeRtmpSocket()
  }

  private fun createSharedObject(): SharedObject {
    val sharedObject = SharedObject("test")
    val data = linkedMapOf<String, AmfData>("random" to AmfNumber(20.0))
    sharedObject.addEvent(SharedObjectEvent(SharedObjectEventType.CHANGE, data))
    return sharedObject
  }

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected shared object amf0 packet`() =
    runTest {
      val buffer = byteArrayOf(2, 0, 0, 0, 0, 0, 40, 19, 0, 0, 0, 0, 0, 4, 116, 101, 115, 116, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 17, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)
      socket.setInputBytes(buffer)
      val sharedObject = createSharedObject()

      val message =
        RtmpMessage.getRtmpMessage(socket, RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

      Assert.assertTrue(message is SharedObject)
      Assert.assertEquals(sharedObject.toString(), (message as SharedObject).toString())
    }

  @Test
  fun `GIVEN a shared object amf0 packet WHEN write into a buffer THEN get expected buffer`() =
    runTest {
      val expectedBuffer = byteArrayOf(2, 0, 0, 0, 0, 0, 40, 19, 0, 0, 0, 0, 0, 4, 116, 101, 115, 116, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 17, 0, 6, 114, 97, 110, 100, 111, 109, 0, 64, 52, 0, 0, 0, 0, 0, 0)

      val sharedObject = createSharedObject()

      sharedObject.writeHeader(socket)
      sharedObject.writeBody(socket, RtmpConfig.DEFAULT_CHUNK_SIZE)

      Assert.assertArrayEquals(expectedBuffer, socket.output.toByteArray())
    }
}