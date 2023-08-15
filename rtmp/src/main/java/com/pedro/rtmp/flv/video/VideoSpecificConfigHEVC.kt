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

/**
 * Created by pedro on 14/08/23.
 *
 * ISO/IEC 14496-15 8.3.3.1.2
 *
 * aligned(8) class HEVCDecoderConfigurationRecord {
 *    unsigned int(8) configurationVersion = 1;
 *    unsigned int(2) general_profile_space;
 *    unsigned int(1) general_tier_flag;
 *    unsigned int(5) general_profile_idc;
 *    unsigned int(32) general_profile_compatibility_flags;
 *    unsigned int(48) general_constraint_indicator_flags;
 *    unsigned int(8) general_level_idc;
 *    bit(4) reserved = ‘1111’b;
 *    unsigned int(12) min_spatial_segmentation_idc;
 *    bit(6) reserved = ‘111111’b;
 *    unsigned int(2) parallelismType;
 *    bit(6) reserved = ‘111111’b;
 *    unsigned int(2) chroma_format_idc;
 *    bit(5) reserved = ‘11111’b;
 *    unsigned int(3) bit_depth_luma_minus8;
 *    bit(5) reserved = ‘11111’b;
 *    unsigned int(3) bit_depth_chroma_minus8;
 *    bit(16) avgFrameRate;
 *    bit(2) constantFrameRate;
 *    bit(3) numTemporalLayers;
 *    bit(1) temporalIdNested;
 *    unsigned int(2) lengthSizeMinusOne;
 *    unsigned int(8) numOfArrays;
 *    for (j=0; j < numOfArrays; j++) {
 *       bit(1) array_completeness;
 *       unsigned int(1) reserved = 0;
 *       unsigned int(6) NAL_unit_type;
 *       unsigned int(16) numNalus;
 *       for (i=0; i< numNalus; i++) {
 *          unsigned int(16) nalUnitLength;
 *          bit(8*nalUnitLength) nalUnit;
 *       }
 *    }
 * }
 */
class VideoSpecificConfigHEVC(
  private val sps: ByteArray,
  private val pps: ByteArray,
  private val vps: ByteArray,
) {

  val size = calculateSize(sps, pps, vps)

  fun write(buffer: ByteArray, offset: Int) {
    val data = ByteBuffer.wrap(buffer, offset, size)
    data.put(0x01) //8 bits version

    //fake header
    val l = listOf(
      0x01,
      0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x5d, 0xf0, 0x00, 0xfc, 0xfd, 0xf8, 0xf8, 0x00, 0x00, 0x0f
    ).map { it.toByte() }.toByteArray()
    data.put(l)

    data.put(0x03) //8 bits num of arrays
    //Arrays
    //array vps
    writeNaluArray(0x20, vps, data)
    //array sps
    writeNaluArray(0x21, sps, data)
    //array pps
    writeNaluArray(0x22, pps, data)
  }

  private fun writeNaluArray(type: Byte, naluByteBuffer: ByteArray, buffer: ByteBuffer) {
    buffer.put(type)
    buffer.put(0x00)
    buffer.put(0x01)
    buffer.putShort(naluByteBuffer.size.toShort())
    buffer.put(naluByteBuffer)
  }

  private fun calculateSize(sps: ByteArray, pps: ByteArray, vps: ByteArray): Int {
    return 23 + 5 + vps.size + 5 + sps.size + 5 + pps.size
  }
}