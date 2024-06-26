/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.message.control.UserControl
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
class UserControlTest {

  private val commandSessionHistory = CommandSessionHistory()

  @Test
  fun `GIVEN a buffer WHEN read rtmp message THEN get expected user control packet`() {
    val buffer = byteArrayOf(2, 0, 0, 0, 0, 0, 6, 4, 0, 0, 0, 0, 0, 6, -1, -1, -1, -1)
    val userControl = UserControl()

    val message = RtmpMessage.getRtmpMessage(ByteArrayInputStream(buffer), RtmpConfig.DEFAULT_CHUNK_SIZE, commandSessionHistory)

    assertTrue(message is UserControl)
    assertEquals(userControl.toString(), (message as UserControl).toString())
  }

  @Test
  fun `GIVEN an user control packet WHEN write into a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(2, 0, 0, 0, 0, 0, 6, 4, 0, 0, 0, 0, 0, 6, -1, -1, -1, -1)
    val output = ByteArrayOutputStream()

    val userControl = UserControl()
    userControl.writeHeader(output)
    userControl.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}