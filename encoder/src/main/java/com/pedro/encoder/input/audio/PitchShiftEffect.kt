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
package com.pedro.encoder.input.audio

import kotlin.math.PI
import kotlin.math.cos

/**
 * [CustomAudioEffect] that shifts the pitch up (or down) keeping the output buffer
 * exactly the same size as the input, so it never breaks the audio pipeline.
 * With [pitch] > 1 you get the typical "chipmunk"/squirrel voice.
 *
 * Unlike a plain resample (which would change the number of samples and therefore
 * the buffer size), this is a time-domain pitch shifter: a delay line whose read
 * position drifts against the write position at the [pitch] ratio. Two read taps
 * offset by half the window are Hann-crossfaded so the wrap-around is inaudible
 * (the two Hann windows offset by half a period sum to 1 → constant amplitude).
 * Because it produces one output sample per input sample, the length is preserved.
 *
 * Expects 16 bit little endian PCM (the format the microphone delivers). It is
 * designed for mono; interleaved stereo still sounds fine but bleeds slightly
 * between channels at the tap boundary.
 *
 */
class PitchShiftEffect(windowSize: Int = 1024) : CustomAudioEffect() {

  /**
   * Pitch ratio. 1f keeps the original pitch, values > 1f raise it (chipmunk),
   * values < 1f lower it. Clamped to a sane range to avoid heavy aliasing.
   */
  var pitch = 1.8f
    set(value) { field = value.coerceIn(0.5f, 3f) }

  private val size = windowSize.coerceAtLeast(2)
  private val ring = FloatArray(size)
  private var writePos = 0
  private var delay = 0f

  override fun process(pcmBuffer: ByteArray): ByteArray {
    val samples = pcmBuffer.size / 2
    val step = pitch - 1f
    val half = size / 2f
    for (i in 0 until samples) {
      val idx = i * 2
      //read one 16 bit little endian sample and store it in the delay line
      val input = ((pcmBuffer[idx].toInt() and 0xFF) or (pcmBuffer[idx + 1].toInt() shl 8)).toShort()
      ring[writePos] = input.toFloat()

      //two taps offset by half the window, Hann-crossfaded (the two windows sum to 1)
      var out = 0f
      for (tap in 0 until 2) {
        var d = delay + tap * half
        if (d >= size) d -= size
        //read position "d" samples behind the write head, wrapped into the ring
        var readPos = writePos - d
        if (readPos < 0f) readPos += size
        val i0 = readPos.toInt()
        val i1 = if (i0 + 1 >= size) 0 else i0 + 1
        val frac = readPos - i0
        val sample = ring[i0] * (1f - frac) + ring[i1] * frac
        val window = 0.5f * (1f - cos(2.0 * PI * (d / size)).toFloat())
        out += sample * window
      }

      //advance write head and drift the delay at the pitch ratio (wraps around the ring)
      if (++writePos >= size) writePos = 0
      delay -= step
      if (delay < 0f) delay += size else if (delay >= size) delay -= size

      //write the result back as 16 bit little endian, clamped to avoid overflow
      val s = out.toInt().coerceIn(-32768, 32767)
      pcmBuffer[idx] = (s and 0xFF).toByte()
      pcmBuffer[idx + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return pcmBuffer
  }
}
