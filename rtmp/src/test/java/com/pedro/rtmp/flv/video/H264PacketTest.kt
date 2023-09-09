package com.pedro.rtmp.flv.video

import android.media.MediaCodec
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 9/9/23.
 */
class H264PacketTest {

  @Test
  fun `GIVEN a h264 buffer WHEN call create a h264 packet 1 time THEN return config and expected buffer`() {
    val timestamp = 123456789L
    val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
    val fakeH264 = header.plus(ByteArray(300) { 0x00 })
    val expectedConfig = byteArrayOf(23, 0, 0, 0, 0, 1, 100, 0, 30, -1, -31, 0, 17, 103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8, 1, 0, 4, 104, -18, 13, -117)
    val expectedFlvPacket = byteArrayOf(23, 1, 0, 0, 0, 0, 0, 1, 45, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    val info = MediaCodec.BufferInfo()
    info.presentationTimeUs = timestamp
    info.offset = 0
    info.size = fakeH264.size
    info.flags = 1
    val h264Packet = H264Packet()
    val sps = byteArrayOf(103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8)
    val pps = byteArrayOf(104, -18, 13, -117)

    h264Packet.sendVideoInfo(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps))
    val frames = mutableListOf<FlvPacket>()
    h264Packet.createFlvVideoPacket(ByteBuffer.wrap(fakeH264), info) { flvPacket ->
      assertEquals(FlvType.VIDEO, flvPacket.type)
      frames.add(flvPacket)
    }

    assertEquals(2, frames.size)
    assertArrayEquals(expectedConfig, frames[0].buffer)
    assertArrayEquals(expectedFlvPacket, frames[1].buffer)
  }
}