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

import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.chunkPackets
import com.pedro.srt.utils.toByteArray
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class OpusPacket(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val length = fixedBuffer.remaining()
    if (length < 0) return

    val header = createControlHeader(length)
    val payload = ByteArray(length + header.size)
    fixedBuffer.get(payload, header.size, length)
    System.arraycopy(header, 0, payload, 0, header.size)

    val pes = Pes(psiManager.getAudioPid().toInt(), true, PesType.PRIVATE_STREAM_1, mediaFrame.info.timestamp, ByteBuffer.wrap(payload))
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes)).chunkPackets(chunkSize).map { buffer ->
      MpegTsPacket(buffer, MpegType.AUDIO, PacketPosition.SINGLE, true)
    }
    if (mpeg2tsPackets.isNotEmpty()) callback(mpeg2tsPackets)
  }

  override fun resetPacket(resetInfo: Boolean) { }

  private fun createControlHeader(payloadLength: Int): ByteArray {
    val bytes = payloadLength.toByteArray()
    val header = ByteArray(2 + bytes.size)
    //header prefix 11b, 0x3ff
    //start_trim_flag 1b, disabled set to 0
    //end_trim_flag 1b, disabled set to 0
    //control_extension_flag 1b, disabled set to 0
    //Reserved 2b, always 0
    header[0] = 0x7F.toByte()
    header[1] = 0xe0.toByte()

    System.arraycopy(bytes, 0, header, 2, bytes.size)
    return header
  }
}