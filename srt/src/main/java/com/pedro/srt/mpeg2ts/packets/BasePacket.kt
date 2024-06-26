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
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.psi.PsiManager
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 *
 * ISO/IEC 13818-1
 *
 * Header (4 bytes):
 *
 * Sync byte -> 8 bits
 * Transport error indicator (TEI) -> 1 bit
 * Payload unit start indicator (PUSI) -> 1 bit
 * Transport priority -> 1 bit
 * PID -> 13 bits
 * Transport scrambling control (TSC) -> 2 bits
 * Adaptation field control -> 2 bits
 * Continuity counter -> 4 bits
 *
 * Optional fields
 * Adaptation field -> variable
 * Payload data -> variable
 */
abstract class BasePacket(
  val psiManager: PsiManager,
  private var limitSize: Int,
) {

  protected val mpegTsPacketizer =  MpegTsPacketizer(psiManager)
  protected var chunkSize = limitSize / MpegTsPacketizer.packetSize //max number of ts packets per srtpacket

  abstract fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (List<MpegTsPacket>) -> Unit
  )

  abstract fun resetPacket(resetInfo: Boolean)

  fun reset(resetInfo: Boolean) {
    mpegTsPacketizer.reset()
    resetPacket(resetInfo)
  }

  fun setLimitSize(limitSize: Int) {
    this.limitSize = limitSize
    chunkSize = limitSize / MpegTsPacketizer.packetSize
  }
}