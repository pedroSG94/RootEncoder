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

package com.pedro.rtmp.flv.video.config

import com.pedro.rtmp.utils.toByteArray
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/08/23.
 *
 * ISO/IEC 14496-15 8.3.3.1.2
 *
 * HEVCDecoderConfigurationRecord
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

    val configurationVersion = 1
    data.put(configurationVersion.toByte())

    val spsParsed = SPSH265Parser()
    spsParsed.parse(sps)

    val generalProfileSpace = spsParsed.generalProfileSpace
    val generalTierFlag = spsParsed.generalTierFlag
    val generalProfileIdc = spsParsed.generalProfileIdc
    val combined = (generalProfileSpace shl 6) or (generalTierFlag shl 5) or generalProfileIdc
    data.put(combined.toByte())

    val generalProfileCompatibilityFlags = spsParsed.generalProfileCompatibilityFlags
    data.putInt(generalProfileCompatibilityFlags)

    val generalConstraintIndicatorFlags = spsParsed.generalConstraintIndicatorFlags
    data.put(generalConstraintIndicatorFlags.toByteArray().sliceArray(2 until Long.SIZE_BYTES))

    val generalLevelIdc = spsParsed.generalLevelIdc
    data.put(generalLevelIdc.toByte())

    val minSpatialSegmentationIdc = 0 //should be extracted from VUI. It is not relevant so using 0 by default
    data.putShort((0xf000 or minSpatialSegmentationIdc).toShort())

    val parallelismType = 0
    data.put((0xfc or parallelismType).toByte())

    val chromaFormatIdc = spsParsed.chromaFormat
    data.put((0xfc or chromaFormatIdc).toByte())

    val bitDepthLumaMinus8 = spsParsed.bitDepthLumaMinus8
    data.put((0xf8 or bitDepthLumaMinus8).toByte())

    val bitDepthChromaMinus8 = spsParsed.bitDepthChromaMinus8
    data.put((0xf8 or bitDepthChromaMinus8).toByte())

    val avgFrameRate = 0
    data.putShort(avgFrameRate.toShort())

    val constantFrameRate = 0
    val numTemporalLayers = 0
    val temporalIdNested = 0
    val lengthSizeMinusOne = 3 //that means that we are using nalu size of 4
    val combined2 = (constantFrameRate shl 6) or (numTemporalLayers shl 3) or (temporalIdNested shl 2) or lengthSizeMinusOne
    data.put(combined2.toByte())

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
    val arrayCompleteness = 1
    val reserved = 0
    buffer.put(((arrayCompleteness shl 7) or (reserved shl 6) or type.toInt()).toByte())
    val numNalus = 1
    buffer.putShort(numNalus.toShort())
    buffer.putShort(naluByteBuffer.size.toShort())
    buffer.put(naluByteBuffer)
  }

  private fun calculateSize(sps: ByteArray, pps: ByteArray, vps: ByteArray): Int {
    return 23 + 5 + vps.size + 5 + sps.size + 5 + pps.size
  }
}