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

import com.pedro.common.BitBuffer
import com.pedro.common.av1.Av1Parser
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

  private val av1Parser = Av1Parser()
  val size = 4 + sequenceObu.size

  fun write(buffer: ByteArray, offset: Int) {
    val obuData = av1Parser.getObus(sequenceObu)[0].data
    val bitBuffer = BitBuffer(ByteBuffer.wrap(obuData))

    val seqProfile = bitBuffer.getInt(3)
    bitBuffer.skipBool()
    val reducedStillPictureHeader = bitBuffer.getBool()
    var seqLevelIdx = 0
    var seqTier = false
    var initialDisplayDelayPresentFlag = false
    var initialPresentationDelay = 0
    if (reducedStillPictureHeader) {
      seqLevelIdx = bitBuffer.getInt(5)
    } else {
      val timingInfoPresentFlag = bitBuffer.getBool()
      var decoderModelInfoPresentFlag = false
      var bufferDelayLengthMinus1 = 0
      if (timingInfoPresentFlag) {
        bitBuffer.skip(64)
        val equalPictureInterval = bitBuffer.getBool()
        if (equalPictureInterval) {
          bitBuffer.readUVLC()
        }
        decoderModelInfoPresentFlag = bitBuffer.getBool()
        if (decoderModelInfoPresentFlag) {
          bufferDelayLengthMinus1 = bitBuffer.getInt(5)
          bitBuffer.skip(42) //skip this
        }
      }
      initialDisplayDelayPresentFlag = bitBuffer.getBool()
      val operatingPointsCntMinus1 = bitBuffer.getInt(5)
      for (i in 0..operatingPointsCntMinus1) {
        bitBuffer.skip(12) //skip
        val levelIdx = bitBuffer.getInt(5)
        if (i == 0) seqLevelIdx = levelIdx
        if (levelIdx > 7) {
          val sTier = bitBuffer.getBool()
          if (i == 0) seqTier = sTier
        }
        if (decoderModelInfoPresentFlag) {
          val decoderModelPresentForThisOp = bitBuffer.getBool()
          if (decoderModelPresentForThisOp) {
            val n = bufferDelayLengthMinus1 + 1
            bitBuffer.skip(n * 2 + 1) //skip this
          }
        }
        if (initialDisplayDelayPresentFlag) {
          val initialDisplayDelayPresentForThisOp = bitBuffer.getBool()
          if (initialDisplayDelayPresentForThisOp) {
            val initialDisplayDelayMinus1 = bitBuffer.getInt(4)
            if (i == 0) initialPresentationDelay = initialDisplayDelayMinus1
          }
        }
      }
    }

    val frameWidthBitsMinus1 = bitBuffer.getInt(4)
    val frameHeightBitsMinus1 = bitBuffer.getInt(4)
    bitBuffer.skip(frameWidthBitsMinus1 + 1 + frameHeightBitsMinus1 + 1)
    var frameIdNumbersPresentFlag = false
    if (!reducedStillPictureHeader) {
      frameIdNumbersPresentFlag = bitBuffer.getBool()
    }
    if (frameIdNumbersPresentFlag) bitBuffer.skip(7)
    bitBuffer.skip(3)
    if (!reducedStillPictureHeader) {
      bitBuffer.skip(4)
      val enableOrderHint = bitBuffer.getBool()
      if (enableOrderHint) bitBuffer.skip(2)
      val seqChooseScreenContentTools = bitBuffer.getBool()
      var seqForceScreenContentTools = false
      if (!seqChooseScreenContentTools) {
        seqForceScreenContentTools = bitBuffer.getBool()
      }
      if (seqForceScreenContentTools) {
        val seqChooseIntegerMv = bitBuffer.getBool()
        if (!seqChooseIntegerMv) bitBuffer.skipBool()
      }
      if (enableOrderHint) bitBuffer.skip(3)
    }
    bitBuffer.skip(3)
    //config color
    val highBitDepth = bitBuffer.getBool()
    var twelveBit = false
    var bitDepth = 0
    if (seqProfile == 2 && highBitDepth) {
      twelveBit = bitBuffer.getBool()
      bitDepth = if (twelveBit) 12 else 10
    } else if (seqProfile <= 2) {
      bitDepth = if (highBitDepth) 10 else 8
    }
    val monochrome = if (seqProfile == 1) {
      false
    } else {
      val chrome = bitBuffer.getBool()
      chrome
    }
    val colorDescriptionPresentFlag = bitBuffer.getBool()
    var colorPrimaries = 0
    var transferCharacteristics = 0
    var matrixCoefficients = 0
    if (colorDescriptionPresentFlag) {
      colorPrimaries = bitBuffer.getInt(8)
      transferCharacteristics = bitBuffer.getInt(8)
      matrixCoefficients = bitBuffer.getInt(8)
    }
    val subsamplingX: Boolean
    val subsamplingY: Boolean
    var samplePosition = false
    if (monochrome) {
      bitBuffer.getBool()
      subsamplingX = true
      subsamplingY = true
    } else if (colorPrimaries == 1 && transferCharacteristics == 1 && matrixCoefficients == 1) {
      subsamplingX = false
      subsamplingY = false
    } else {
      bitBuffer.skipBool()
      if (seqProfile == 0) {
        subsamplingX = true
        subsamplingY = true
      } else if (seqProfile == 1) {
        subsamplingX = false
        subsamplingY = false
      } else {
        if (bitDepth == 12) {
          subsamplingX = bitBuffer.getBool()
          subsamplingY = if (subsamplingX) {
            bitBuffer.getBool()
          } else {
            false
          }
        } else {
          subsamplingX = true
          subsamplingY = false
        }
        if (subsamplingX && subsamplingY) {
          samplePosition = bitBuffer.getBool()
        }
      }
    }
    //finish config color
    val data = ByteBuffer.wrap(buffer, offset, size)
    data.put(0x81.toByte()) //marker and version
    data.put(((seqProfile shl 5) or seqLevelIdx).toByte())
    data.put(
      ((seqTier.toInt() shl 7) or (highBitDepth.toInt() shl 6) or (twelveBit.toInt() shl 5) or (monochrome.toInt() shl 4) or
      (subsamplingX.toInt() shl 3) or (subsamplingY.toInt() shl 2) or samplePosition.toInt()).toByte()
    )
    val reserved = 0
    data.put(((reserved shl 5) or (initialDisplayDelayPresentFlag.toInt() shl 4) or initialPresentationDelay).toByte())
    data.put(sequenceObu)
  }
}