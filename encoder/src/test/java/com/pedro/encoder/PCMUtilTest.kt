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
  fun `GIVEN a mono pcm WHEN resample to double rate THEN output has double frames and interpolates values`() {
    // 4 mono samples 16 bits: 0, 100, 200, 300
    val pcm = shortsToBytes(shortArrayOf(0, 100, 200, 300))
    val resampled = PCMUtil.resample(pcm, 1, 8000, 16000)
    // 8 samples, odd indexes interpolated between neighbors (last one clamps to last sample)
    assertArrayEquals(shortArrayOf(0, 50, 100, 150, 200, 250, 300, 300), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN a stereo pcm WHEN resample to half rate THEN average each interval keeping channels independent`() {
    // 4 stereo frames: L = 0, 100, 200, 300 | R = 1000, 1100, 1200, 1300
    val pcm = shortsToBytes(shortArrayOf(0, 1000, 100, 1100, 200, 1200, 300, 1300))
    val resampled = PCMUtil.resample(pcm, 2, 16000, 8000)
    // each output frame averages 2 input frames per channel
    assertArrayEquals(shortArrayOf(50, 1050, 250, 1250), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN same input and output sample rate WHEN resample THEN return the same buffer`() {
    val pcm = shortsToBytes(shortArrayOf(0, 100, 200, 300))
    assertArrayEquals(pcm, PCMUtil.resample(pcm, 1, 44100, 44100))
  }

  @Test
  fun `GIVEN a mono pcm WHEN resample to stereo keeping rate THEN duplicate the channel`() {
    val pcm = shortsToBytes(shortArrayOf(0, 100, 200, 300))
    val resampled = PCMUtil.resample(pcm, 1, 2, 8000, 8000)
    assertArrayEquals(shortArrayOf(0, 0, 100, 100, 200, 200, 300, 300), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN a stereo pcm WHEN resample to mono keeping rate THEN average both channels`() {
    // 3 stereo frames: L = 0, 100, 200 | R = 1000, 1100, 1300
    val pcm = shortsToBytes(shortArrayOf(0, 1000, 100, 1100, 200, 1300))
    val resampled = PCMUtil.resample(pcm, 2, 1, 8000, 8000)
    assertArrayEquals(shortArrayOf(500, 600, 750), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN a mono pcm WHEN resample to stereo and double rate THEN duplicate and interpolate in one pass`() {
    val pcm = shortsToBytes(shortArrayOf(0, 100, 200, 300))
    val resampled = PCMUtil.resample(pcm, 1, 2, 8000, 16000)
    assertArrayEquals(
      shortArrayOf(0, 0, 50, 50, 100, 100, 150, 150, 200, 200, 250, 250, 300, 300, 300, 300),
      bytesToShorts(resampled)
    )
  }

  @Test
  fun `GIVEN a stereo pcm WHEN resample to mono and half rate THEN downmix and average intervals in one pass`() {
    // 4 stereo frames: L = 0, 100, 200, 300 | R = 1000, 1100, 1200, 1300
    // downmixed frames: 500, 600, 700, 800 -> averaged intervals of 2: 550, 750
    val pcm = shortsToBytes(shortArrayOf(0, 1000, 100, 1100, 200, 1200, 300, 1300))
    val resampled = PCMUtil.resample(pcm, 2, 1, 16000, 8000)
    assertArrayEquals(shortArrayOf(550, 750), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN a mono pcm WHEN resample from 32000 to 8000 THEN average intervals of 4 frames`() {
    val pcm = shortsToBytes(shortArrayOf(0, 100, 200, 300, 1000, 1100, 1200, 1300))
    val resampled = PCMUtil.resample(pcm, 1, 32000, 8000)
    assertArrayEquals(shortArrayOf(150, 1150), bytesToShorts(resampled))
  }

  @Test
  fun `GIVEN a stereo pcm WHEN resample THEN output size matches rate ratio`() {
    // 1 second of stereo at 44100Hz resampled to 32000Hz
    val pcm = ByteArray(44100 * 4)
    val resampled = PCMUtil.resample(pcm, 2, 44100, 32000)
    assertEquals(32000 * 4, resampled.size)
  }

  private fun shortsToBytes(samples: ShortArray): ByteArray {
    val bytes = ByteArray(samples.size * 2)
    samples.forEachIndexed { i, sample ->
      bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
      bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return bytes
  }

  private fun bytesToShorts(bytes: ByteArray): ShortArray {
    return ShortArray(bytes.size / 2) { i ->
      ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
    }
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
