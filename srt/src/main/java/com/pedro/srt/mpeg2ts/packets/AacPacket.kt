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

package com.pedro.srt.mpeg2ts.packets

import android.media.MediaCodec
import com.pedro.common.removeInfo
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class AacPacket(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  private val header = ByteArray(7) //ADTS header
  private var sampleRate = 44100
  private var isStereo = true

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    val length = fixedBuffer.remaining()
    if (length < 0) return

    val payload = ByteArray(length + header.size)
    writeAdts(payload, payload.size, 0)
    fixedBuffer.get(payload, header.size, length)

    val pes = Pes(psiManager.getAudioPid().toInt(), false, PesType.AUDIO, info.presentationTimeUs, ByteBuffer.wrap(payload))
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes))
    val chunked = mpeg2tsPackets.chunked(chunkSize)
    val packets = mutableListOf<MpegTsPacket>()
    chunked.forEachIndexed { index, chunks ->
      val size = chunks.sumOf { it.size }
      val buffer = ByteBuffer.allocate(size)
      chunks.forEach {
        buffer.put(it)
      }
      val packetPosition = PacketPosition.SINGLE
      packets.add(MpegTsPacket(buffer.array(), MpegType.AUDIO, packetPosition, false))
    }
    callback(packets)
  }

  override fun resetPacket(resetInfo: Boolean) {
    if (resetInfo) {
      sampleRate = 44100
      isStereo = true
    }
  }

  fun sendAudioInfo(sampleRate: Int, stereo: Boolean) {
    this.sampleRate = sampleRate
    this.isStereo = stereo
  }

  private fun writeAdts(buffer: ByteArray, length: Int, offset: Int) {
    val type = 2 //AAC-LC
    val channels = if (isStereo) 2 else 1
    val frequency = getFrequency()
    buffer[offset] = 0xFF.toByte()
    buffer[offset + 1] = 0xF9.toByte()
    buffer[offset + 2] = (((type - 1) shl 6) or (frequency shl 2) or (channels shr 2)).toByte()
    buffer[offset + 3] = (((channels and 3) shl 6) or (length shr 11)).toByte()
    buffer[offset + 4] = ((length and 0x7FF) shr 3).toByte()
    buffer[offset + 5] = (((length and 7) shl 5).toByte()).plus(0x1F).toByte()
    buffer[offset + 6] = 0xFC.toByte()
  }

  private fun getFrequency(): Int {
    var frequency = AUDIO_SAMPLING_RATES.indexOf(sampleRate)
    //sane check, if samplerate not found using default 44100
    if (frequency == -1) frequency = 4
    return frequency
  }

  private val AUDIO_SAMPLING_RATES = intArrayOf(
    96000,  // 0
    88200,  // 1
    64000,  // 2
    48000,  // 3
    44100,  // 4
    32000,  // 5
    24000,  // 6
    22050,  // 7
    16000,  // 8
    12000,  // 9
    11025,  // 10
    8000,  // 11
    7350,  // 12
    -1,  // 13
    -1,  // 14
    -1)
}