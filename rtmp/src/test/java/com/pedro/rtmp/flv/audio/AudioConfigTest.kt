package com.pedro.rtmp.flv.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AudioConfigTest {

  @Test
  fun `GIVEN sps and pps WHEN create a video config for sequence packet THEN return a bytearray with the config`() {
    val sampleRate = 44100
    val isStereo = true
    val objectType = AudioObjectType.AAC_LC
    val expectedConfig = byteArrayOf(18, 16, -1, -7, 80, -128, 1, 63, -4)

    val config = AudioSpecificConfig(objectType.value, sampleRate, if (isStereo) 2 else 1)
    val data = ByteArray(config.size)
    config.write(data, 0)
    assertArrayEquals(expectedConfig, data)
  }
}