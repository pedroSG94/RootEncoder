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

import android.util.Log
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.getData
import com.pedro.common.nal.NalReader
import com.pedro.common.removeInfo
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.chunkPackets
import com.pedro.srt.utils.getPayload
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 *
 * Used for H264/H265
 */
class H265Packet(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  private val TAG = "H265Packet"

  private var sps: ByteBuffer? = null
  private var pps: ByteBuffer? = null
  private var vps: ByteBuffer? = null

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val isKeyFrame = mediaFrame.info.isKeyFrame
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H265, false)
    if (nals.isEmpty()) return

    val sps = this.sps
    val pps = this.pps
    val vps = this.vps
    if (sps == null || pps == null || vps == null) {
      Log.e(TAG, "waiting for a valid video info")
      return
    }

    if (isKeyFrame) {
      if (!nals.contains(pps)) nals.add(0, pps.duplicate())
      if (!nals.contains(sps)) nals.add(0, sps.duplicate())
      if (!nals.contains(vps)) nals.add(0, vps.duplicate())
    }

    val payload = nals.getPayload {
      ByteBuffer.allocate(7).apply {
        putInt(0x00000001) //annex-b header
        put(0x46.toByte())
        put(0x01.toByte())
        put(0x50.toByte())
        flip()
      }
    }
    val pes = Pes(psiManager.getVideoPid().toInt(), isKeyFrame, PesType.VIDEO, mediaFrame.info.timestamp, payload)
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes)).chunkPackets(chunkSize).map { buffer ->
      MpegTsPacket(buffer, MpegType.VIDEO, PacketPosition.SINGLE, isKeyFrame)
    }
    if (mpeg2tsPackets.isNotEmpty()) callback(mpeg2tsPackets)
  }

  override fun resetPacket(resetInfo: Boolean) {
    if (resetInfo) {
      vps = null
      sps = null
      pps = null
    }
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
    this.sps = ByteBuffer.wrap(sps.getData())
    this.pps = ByteBuffer.wrap(pps.getData())
    this.vps = ByteBuffer.wrap(vps.getData())
  }
}