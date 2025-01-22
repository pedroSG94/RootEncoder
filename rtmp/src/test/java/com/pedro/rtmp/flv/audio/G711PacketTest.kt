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

package com.pedro.rtmp.flv.audio

import com.pedro.common.frame.MediaFrame
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.packet.G711Packet
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 21/12/23.
 */
class G711PacketTest {

  @Test
  fun `GIVEN a G711 buffer WHEN call create a G711 packet THEN expected buffer`() = runTest {
    val timestamp = 123456789L
    val buffer = ByteArray(256) { 0x00 }
    val info = MediaFrame.Info(0, buffer.size, timestamp, false)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(buffer), info, MediaFrame.Type.AUDIO)
    val g711Packet = G711Packet()
    g711Packet.sendAudioInfo()
    g711Packet.createFlvPacket(mediaFrame) { flvPacket ->
      assertEquals(FlvType.AUDIO, flvPacket.type)
      assertEquals(0x72, flvPacket.buffer[0])
      assertEquals(buffer.size + 1, flvPacket.length)
    }
  }
}