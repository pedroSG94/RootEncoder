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
 *
 *
 */
class H265Packet(
  private val sizeLimit: Int,
  psiManager: PSIManager
): BasePacket(psiManager) {

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  ) {
    TODO("Not yet implemented")
  }

  private fun generatePMTProgramDescription(): ByteArray {
    val bytes = ByteArray(6)
    bytes[0] = 0x05
    bytes[1] = 0x04

    bytes[2] = "H".toByte()
    bytes[3] = "E".toByte()
    bytes[4] = "V".toByte()
    bytes[5] = "C".toByte()
    return bytes
  }
}