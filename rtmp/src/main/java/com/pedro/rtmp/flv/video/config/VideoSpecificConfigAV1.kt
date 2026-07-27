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

import com.pedro.common.av1.Av1SequenceHeaderParser
import com.pedro.common.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/12/23.
 * aligned(8) class AV1CodecConfigurationRecord
 * {
 *   unsigned int(1) marker = 1;
 *   unsigned int(7) version = 1;
 *   unsigned int(3) seq_profile;
 *   unsigned int(5) seq_level_idx_0;
 *   unsigned int(1) seq_tier_0;
 *   unsigned int(1) high_bitdepth;
 *   unsigned int(1) twelve_bit;
 *   unsigned int(1) monochrome;
 *   unsigned int(1) chroma_subsampling_x;
 *   unsigned int(1) chroma_subsampling_y;
 *   unsigned int(2) chroma_sample_position;
 *   unsigned int(3) reserved = 0;
 *
 *   unsigned int(1) initial_presentation_delay_present;
 *   if(initial_presentation_delay_present) {
 *     unsigned int(4) initial_presentation_delay_minus_one;
 *   } else {
 *     unsigned int(4) reserved = 0;
 *   }
 *
 *   unsigned int(8) configOBUs[];
 * }
 *
 */
class VideoSpecificConfigAV1(private val sequenceObu: ByteArray) {

  val size = 4 + sequenceObu.size

  fun write(buffer: ByteArray, offset: Int) {
    val sequenceHeader = Av1SequenceHeaderParser()
    sequenceHeader.parse(sequenceObu)
    //finish config color
    val data = ByteBuffer.wrap(buffer, offset, size)
    data.put(0x81.toByte()) //marker and version
    data.put(((sequenceHeader.seqProfile shl 5) or sequenceHeader.seqLevelIdx).toByte())
    data.put(
      ((sequenceHeader.seqTier.toInt() shl 7) or (sequenceHeader.highBitDepth.toInt() shl 6) or (sequenceHeader.twelveBit.toInt() shl 5) or (sequenceHeader.monochrome.toInt() shl 4) or
      (sequenceHeader.subsamplingX.toInt() shl 3) or (sequenceHeader.subsamplingY.toInt() shl 2) or sequenceHeader.samplePosition.toInt()).toByte()
    )
    val reserved = 0
    data.put(((reserved shl 5) or (sequenceHeader.initialDisplayDelayPresentFlag.toInt() shl 4) or sequenceHeader.initialPresentationDelay).toByte())
    data.put(sequenceObu)
  }
}
