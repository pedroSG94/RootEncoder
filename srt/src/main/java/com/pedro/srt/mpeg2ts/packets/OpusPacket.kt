/*
 * Copyright (C) 2023 pedroSG94.
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
class OpusPacket(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  var sampleRate = 48000
  var isStereo = true

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    val length = fixedBuffer.remaining()
    if (length < 0) return

    val header = createControlHeader(length)
    val payload = ByteArray(length + header.size)
    fixedBuffer.get(payload, header.size, length)
    System.arraycopy(header, 0, payload, 0, header.size)

    val pes = Pes(psiManager.getAudioPid().toInt(), true, PesType.PRIVATE_STREAM_1, info.presentationTimeUs, ByteBuffer.wrap(payload))
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
      packets.add(MpegTsPacket(buffer.array(), MpegType.AUDIO, packetPosition, true))
    }
    callback(packets)
  }

  override fun resetPacket(resetInfo: Boolean) {
    if (resetInfo) {
      sampleRate = 48000
      isStereo = true
    }
  }

  fun sendAudioInfo(sampleRate: Int, stereo: Boolean) {
    this.sampleRate = sampleRate
    this.isStereo = stereo
  }

  private fun createControlHeader(payloadLength: Int): ByteArray {
//    val bytes = payloadLength.toByteArray()
//    val header = ByteArray(2 + bytes.size)
//    //header prefix
//    header[0] = 0xFF.toByte()
//    header[1] = 0xC0.toByte()
//    //start_trim_flag 1b
//    //end_trim_flag 1b
//    //control_extension_flag 1b
//    //Reserved 2b
//    System.arraycopy(bytes, 0, header, 2, bytes.size)
//    return header
    return byteArrayOf()
  }
}