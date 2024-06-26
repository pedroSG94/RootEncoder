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

import com.pedro.rtmp.utils.BitBuffer
import java.nio.ByteBuffer

/**
 * Created by pedro on 16/08/23.
 *
 * ISO/IEC 23008-2 7.3.2.2.1
 *
 */
class SPSH265Parser {
  var generalProfileSpace = 0
  var generalTierFlag = 0
  var generalProfileIdc = 0
  var generalProfileCompatibilityFlags = 0
  var generalConstraintIndicatorFlags = 0L
  var generalLevelIdc = 0
  var chromaFormat = 0
  var bitDepthLumaMinus8 = 0
  var bitDepthChromaMinus8 = 0

  fun parse(sps: ByteArray) {
    parse(ByteBuffer.wrap(sps))
  }

  fun parse(sps: ByteBuffer) {
    val rbsp = BitBuffer.extractRbsp(sps, 2)
    val bitBuffer = BitBuffer(rbsp)
    //Dropping nal_unit_header
    bitBuffer.getLong(16)
    //sps_video_parameter_set_id
    bitBuffer.get(4)
    //sps_max_sub_layers_minus1
    val maxSubLayersMinus1 = bitBuffer.get(3)
    //sps_temporal_id_nesting_flag
    bitBuffer.get(1)
    //start profile_tier_level
    generalProfileSpace = bitBuffer.get(2).toInt()
    generalTierFlag = if (bitBuffer.getBool()) 1 else 0
    generalProfileIdc = bitBuffer.getShort(5).toInt()

    generalProfileCompatibilityFlags = bitBuffer.getInt(32)
    generalConstraintIndicatorFlags = bitBuffer.getLong(48)
    generalLevelIdc = bitBuffer.get(8).toInt()

    val subLayerProfilePresentFlag = mutableListOf<Boolean>()
    val subLayerLevelPresentFlag = mutableListOf<Boolean>()
    for (i in 0 until maxSubLayersMinus1) {
      subLayerProfilePresentFlag.add(bitBuffer.getBool())
      subLayerLevelPresentFlag.add(bitBuffer.getBool())
    }

    if (maxSubLayersMinus1 > 0) {
      for (i in maxSubLayersMinus1..8) {
        bitBuffer.getLong(2) // reserved_zero_2bits
      }
    }

    for (i in 0 until maxSubLayersMinus1) {
      if (subLayerProfilePresentFlag[i]) {
        bitBuffer.getLong(32) // skip
        bitBuffer.getLong(32) // skip
        bitBuffer.getLong(24) // skip
      }

      if (subLayerLevelPresentFlag[i]) {
        bitBuffer.getLong(8) // skip
      }
    }
    //end profile_tier_level

    //sps_seq_parameter_set_id
    bitBuffer.readUE()
    //chroma_format_idc
    chromaFormat = bitBuffer.readUE()
    if (chromaFormat == 3) {
      //separate_colour_plane_flag
      bitBuffer.getBool()
    }
    //pic_width_in_luma_samples
    bitBuffer.readUE()
    //pic_height_in_luma_samples
    bitBuffer.readUE()
    //conformance_window_flag
    val conformanceWindowFlag = bitBuffer.getBool()
    if (conformanceWindowFlag) {
      //conf_win_left_offset
      bitBuffer.readUE()
      //conf_win_right_offset
      bitBuffer.readUE()
      //conf_win_top_offset
      bitBuffer.readUE()
      //conf_win_bottom_offset
      bitBuffer.readUE()
    }
    //bit_depth_luma_minus8
    bitDepthLumaMinus8 = bitBuffer.readUE()
    //bit_depth_chroma_minus8
    bitDepthChromaMinus8 = bitBuffer.readUE()

    //The buffer continue but we don't need read more
  }
}