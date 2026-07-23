package com.pedro.encoder

import com.pedro.encoder.input.audio.PitchShiftEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchShiftEffectTest {

  @Test
  fun `keeps the same buffer size for several sizes and pitches`() {
    for (window in intArrayOf(256, 1024, 2048)) {
      val effect = PitchShiftEffect(window)
      for (pitch in floatArrayOf(0.5f, 1f, 1.8f, 3f)) {
        effect.pitch = pitch
        for (bytes in intArrayOf(64, 1024, 8192)) {
          val out = effect.process(ByteArray(bytes) { (it and 0xFF).toByte() })
          assertEquals(bytes, out.size)
        }
      }
    }
  }

  @Test
  fun `raises pitch (about twice the zero crossings) when pitch is 2`() {
    val fs = 44100.0
    val freq = 440.0
    val n = 16384
    val inCrossings = zeroCrossings(sineBytes(n, freq, fs))

    val effect = PitchShiftEffect().apply { pitch = 2f }
    effect.process(sineBytes(n, freq, fs)) //warm up the delay line
    val outCrossings = zeroCrossings(effect.process(sineBytes(n, freq, fs)))

    assertTrue(
      "expected more zero crossings after pitch up: in=$inCrossings out=$outCrossings",
      outCrossings > inCrossings * 1.5
    )
  }

  private fun sineBytes(samples: Int, freq: Double, fs: Double): ByteArray {
    val out = ByteArray(samples * 2)
    for (i in 0 until samples) {
      val v = (sin(2 * PI * freq * i / fs) * 12000).toInt()
      out[i * 2] = (v and 0xFF).toByte()
      out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
  }

  private fun zeroCrossings(bytes: ByteArray): Int {
    var count = 0
    var prev = 0
    for (i in 0 until bytes.size / 2) {
      val s = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort().toInt()
      if (i > 0 && ((prev < 0 && s >= 0) || (prev >= 0 && s < 0))) count++
      prev = s
    }
    return count
  }
}
