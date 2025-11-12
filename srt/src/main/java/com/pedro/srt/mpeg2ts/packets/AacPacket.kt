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

import com.pedro.common.AudioUtils
import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.chunkPackets
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class AacPacket(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  private var sampleRate = 44100
  private var channels = 2
  private val type = 2 //AAC-LC
  private val headerSize = AudioUtils.ADTS_SIZE

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val length = fixedBuffer.remaining()
    if (length < 0) return

    val payload = ByteArray(length + headerSize)
    val adts = AudioUtils.createAdtsHeader(type, payload.size, sampleRate, channels)
    adts.get(payload, 0, headerSize)
    fixedBuffer.get(payload, headerSize, length)

    val pes = Pes(psiManager.getAudioPid().toInt(), false, PesType.AUDIO, mediaFrame.info.timestamp, ByteBuffer.wrap(payload))
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes)).chunkPackets(chunkSize).map { buffer ->
        MpegTsPacket(buffer, MpegType.AUDIO, PacketPosition.SINGLE, isKey = false)
    }
    if (mpeg2tsPackets.isNotEmpty()) callback(mpeg2tsPackets)
  }

  override fun resetPacket(resetInfo: Boolean) { }

  fun sendAudioInfo(sampleRate: Int, stereo: Boolean) {
    this.sampleRate = sampleRate
    channels = if (stereo) 2 else 1
  }
}