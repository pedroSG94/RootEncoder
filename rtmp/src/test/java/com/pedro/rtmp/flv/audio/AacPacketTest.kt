package com.pedro.rtmp.flv.audio

import android.media.MediaCodec
import com.pedro.rtmp.flv.FlvType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 9/9/23.
 */
class AacPacketTest {

  @Test
  fun `GIVEN a aac buffer WHEN call create a aac packet 2 times THEN return config and expected buffer`() {
    val timestamp = 123456789L
    val buffer = ByteArray(256) { 0x00 }
    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = buffer.size
    info.flags = 1
    val aacPacket = AacPacket()
    aacPacket.sendAudioInfo(32000, true)
    aacPacket.createFlvAudioPacket(ByteBuffer.wrap(buffer), info) { flvPacket ->
      assertEquals(FlvType.AUDIO, flvPacket.type)
      assertEquals(AacPacket.Type.SEQUENCE.mark, flvPacket.buffer[1])
    }
    aacPacket.createFlvAudioPacket(ByteBuffer.wrap(buffer), info) { flvPacket ->
      assertEquals(FlvType.AUDIO, flvPacket.type)
      assertEquals(AacPacket.Type.RAW.mark, flvPacket.buffer[1])
      assertEquals(buffer.size + 2, flvPacket.length)
    }
  }
}