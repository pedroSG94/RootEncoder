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
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.psi.PSIManager
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class H264Packet(
  private val sizeLimit: Int,
  psiManager: PSIManager
): BasePacket(psiManager) {

  private val headerSize = 6
  private val sps: ByteBuffer? = null
  private val pps: ByteBuffer? = null
  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  ) {
    val length = info.size - info.offset
    val buffer = ByteArray(length + headerSize)
    writeAUD(buffer, 0)

  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {

  }

  private fun writeAUD(buffer: ByteArray, offset: Int) {
    buffer[offset] = 0x00
    buffer[offset + 1] = 0x00
    buffer[offset + 2] = 0x00
    buffer[offset + 3] = 0x01
    buffer[offset + 4] = 0x09
    buffer[offset + 5] = 0xF0.toByte()
  }
}