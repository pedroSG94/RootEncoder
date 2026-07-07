package com.pedro.encoder

import com.pedro.encoder.utils.PCMUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PCMUtilTest {

  @Test
  fun `GIVEN a 3 channel pcm WHEN pcmToStereo THEN keep 16 bit samples of channels 0 and 1`() {
    // 2 frames, 3 channels, 16 bits per sample -> 6 bytes per frame
    // frame0: ch0=(1,2) ch1=(3,4) ch2=(5,6) | frame1: ch0=(7,8) ch1=(9,10) ch2=(11,12)
    val pcm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    val stereo = PCMUtil.pcmToStereo(pcm, 3)
    // expected: L=ch0, R=ch1 per frame
    assertArrayEquals(byteArrayOf(1, 2, 3, 4, 7, 8, 9, 10), stereo)
  }

  @Test
  fun `GIVEN a large multichannel frame WHEN pcmToStereo THEN output size is exact and does not overflow`() {
    // 4 channels, 2000 frames -> input 16000 bytes, stereo output 8000 bytes (old static buffer was 4096)
    val channels = 4
    val frames = 2000
    val pcm = ByteArray(frames * channels * 2)
    val stereo = PCMUtil.pcmToStereo(pcm, channels)
    assertEquals(frames * 4, stereo.size)
  }
}
