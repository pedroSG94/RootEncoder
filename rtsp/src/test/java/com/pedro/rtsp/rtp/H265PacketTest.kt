package com.pedro.rtsp.rtp

import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/4/22.
 */
class H265PacketTest {

  @Test
  fun `GIVEN a small ByteBuffer raw h265 WHEN create a packet THEN get a RTP h265 packet`() {
    val smallH265 = ByteBuffer.wrap(byteArrayOf())
  }

  @Test
  fun `GIVEN a big ByteBuffer raw h265 WHEN create a packet THEN get a RTP h265 packet`() {
    val smallH265 = ByteBuffer.wrap(byteArrayOf())
  }
}