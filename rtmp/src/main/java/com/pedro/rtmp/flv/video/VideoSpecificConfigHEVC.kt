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

package com.pedro.rtmp.flv.video

import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 14/08/23.
 *
 * 1 byte configuration version
 * 2 bits general profile space
 * 1 bit general tier flag
 * 5 bits general profile idc
 * 4 bytes general profile compatibility flags
 * 6 bytes general constraint indicator flags
 * 1 byte general level idc
 * 4 bits reserved (1111)
 * 12 bits min spatial segmentation idc
 * 6 bits reserved (111111)
 * 2 bits parallelism type
 * 6 bits reserved (111111)
 * 2 bits chroma format idc
 * 5 bits reserved (11111)
 * 3 bits bit depth chroma minus8
 * 2 bytes avg frame rate
 * 2 bits constant frame rate
 * 3 bits num temporal layers
 * 1 bit temporal id nested
 * 2 bits length size minus one
 * 1 byte num of arrays
 *
 * Arrays:
 * 1 bit array completeness
 * 1 bit reserved (0)
 * 6 bits nal unit type
 * 2 bytes num nalus
 *
 * Nalu:
 * 2 bytes nal unit length
 * n bytes of nalu content
 */
class VideoSpecificConfigHEVC(
  private val sps: ByteArray,
  private val pps: ByteArray,
  private val vps: ByteArray,
  private val profileIop: ProfileIop
) {

  val size = calculateSize(sps, pps, vps)

  fun write(buffer: ByteArray, offset: Int) {
    val data = ByteBuffer.wrap(buffer, offset, size)

    data.put(0x01) // version
    data.put(ByteArray(18)) // TODO
    data.put(0x03) // num of arrays
    //Arrays
    //array sps
    val nalTypeSps = VideoNalType.HEVC_SPS.value.toByte() and 0b00111111
    data.put(nalTypeSps)
    data.putShort(1.toShort())
    data.putShort(sps.size.toShort())
    data.put(sps)
    //array pps
    val nalTypePps = VideoNalType.HEVC_SPS.value.toByte() and 0b00111111
    data.put(nalTypePps)
    data.putShort(1.toShort())
    data.putShort(pps.size.toShort())
    data.put(pps)
    //array vps
    val nalTypeVps = VideoNalType.HEVC_SPS.value.toByte() and 0b00111111
    data.put(nalTypeVps)
    data.putShort(1.toShort())
    data.putShort(vps.size.toShort())
    data.put(vps)
  }

  private fun calculateSize(sps: ByteArray, pps: ByteArray, vps: ByteArray): Int {
    return 20 + 5 + sps.size + 5 + pps.size + 5 + vps.size
  }
}