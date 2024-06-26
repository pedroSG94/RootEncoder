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
    val bitBuffer = BitBuffer(obuData)
    var index = 0

    val seqProfile = bitBuffer.getBits(index, 3)
    index += 3
    index += 1
    val reducedStillPictureHeader = bitBuffer.getBits(index, 1)
    index += 1
    var seqLevelIdx = 0
    var seqTier = 0
    var initialDisplayDelayPresentFlag = 0
    var initialPresentationDelay = 0
    if (reducedStillPictureHeader == 1) {
      seqLevelIdx = bitBuffer.getBits(index, 5)
      index += 5
    } else {
      val timingInfoPresentFlag = bitBuffer.getBits(index, 1)
      index += 1
      var decoderModelInfoPresentFlag = 0
      var bufferDelayLengthMinus1 = 0
      if (timingInfoPresentFlag == 1) {
        index += 64
        val equalPictureInterval = bitBuffer.getBits(index, 1)
        index += 1
        if (equalPictureInterval == 1) {
          val uvlc = readUVLC(obuData, index)
          index += uvlc.second
        }
        decoderModelInfoPresentFlag = bitBuffer.getBits(index, 1)
        index += 1
        if (decoderModelInfoPresentFlag == 1) {
          bufferDelayLengthMinus1 = bitBuffer.getBits(index, 5)
          index += 5
          index += 42 //skip this
        }
      }
      initialDisplayDelayPresentFlag = bitBuffer.getBits(index, 1)
      index += 1
      val operatingPointsCntMinus1 = bitBuffer.getBits(index, 5)
      index += 5
      for (i in 0..operatingPointsCntMinus1) {
        index += 12 //skip
        val levelIdx = bitBuffer.getBits(index, 5)
        index += 5
        if (i == 0) seqLevelIdx = levelIdx
        if (levelIdx > 7) {
          val sTier = bitBuffer.getBits(index, 1)
          index += 1
          if (i == 0) seqTier = sTier
        }
        if (decoderModelInfoPresentFlag == 1) {
          val decoderModelPresentForThisOp = bitBuffer.getBits(index, 1)
          index += 1
          if (decoderModelPresentForThisOp == 1) {
            val n = bufferDelayLengthMinus1 + 1
            index += n * 2 + 1 //skip this
          }
        }
        if (initialDisplayDelayPresentFlag == 1) {
          val initialDisplayDelayPresentForThisOp = bitBuffer.getBits(index, 1)
          index += 1
          if (initialDisplayDelayPresentForThisOp == 1) {
            val initialDisplayDelayMinus1 = bitBuffer.getBits(index, 4)
            index += 4
            if (i == 0) initialPresentationDelay = initialDisplayDelayMinus1
          }
        }
      }
    }

    val frameWidthBitsMinus1 = bitBuffer.getBits(index, 4)
    index += 4
    val frameHeightBitsMinus1 = bitBuffer.getBits(index, 4)
    index += 4
    index += frameWidthBitsMinus1 + 1 + frameHeightBitsMinus1 + 1
    var frameIdNumbersPresentFlag = 0
    if (reducedStillPictureHeader != 1) {
      frameIdNumbersPresentFlag = bitBuffer.getBits(index, 1)
      index += 1
    }
    if (frameIdNumbersPresentFlag == 1) index += 7
    index += 3
    if (reducedStillPictureHeader != 1) {
      index += 4
      val enableOrderHint = bitBuffer.getBits(index, 1)
      index += 1
      if (enableOrderHint == 1) index += 2
      val seqChooseScreenContentTools = bitBuffer.getBits(index, 1)
      index += 1
      var seqForceScreenContentTools = 2
      if (seqChooseScreenContentTools != 1) {
        seqForceScreenContentTools = bitBuffer.getBits(index, 1)
        index += 1
      }
      if (seqForceScreenContentTools > 0) {
        val seqChooseIntegerMv = bitBuffer.getBits(index, 1)
        index += 1
        if (seqChooseIntegerMv != 1) index += 1
      }
      if (enableOrderHint == 1) index += 3
    }
    index += 3
    //config color
    val highBitDepth = bitBuffer.getBits(index, 1)
    index += 1
    var twelveBit = 0
    var bitDepth = 0
    if (seqProfile == 2 && highBitDepth == 1) {
      twelveBit = bitBuffer.getBits(index, 1)
      index += 1
      bitDepth = if (twelveBit == 1) 12 else 10
    } else if (seqProfile <= 2) {
      bitDepth = if (highBitDepth == 1) 10 else 8
    }
    val monochrome = if (seqProfile == 1) {
      0
    } else {
      val chrome = bitBuffer.getBits(index, 1)
      index += 1
      chrome
    }
    val colorDescriptionPresentFlag = bitBuffer.getBits(index, 1)
    index += 1
    var colorPrimaries = 0
    var transferCharacteristics = 0
    var matrixCoefficients = 0
    if (colorDescriptionPresentFlag == 1) {
      colorPrimaries = bitBuffer.getBits(index, 8)
      index += 8
      transferCharacteristics = bitBuffer.getBits(index, 8)
      index += 8
      matrixCoefficients = bitBuffer.getBits(index, 8)
      index += 8
    }
    val subsamplingX: Int
    val subsamplingY: Int
    var samplePosition = 0
    if (monochrome == 1) {
      index += 1
      subsamplingX = 1
      subsamplingY = 1
    } else if (colorPrimaries == 1 && transferCharacteristics == 1 && matrixCoefficients == 1) {
      subsamplingX = 0
      subsamplingY = 0
    } else {
      index += 1
      if (seqProfile == 0) {
        subsamplingX = 1
        subsamplingY = 1
      } else if (seqProfile == 1) {
        subsamplingX = 0
        subsamplingY = 0
      } else {
        if (bitDepth == 12) {
          subsamplingX = bitBuffer.getBits(index, 1)
          index += 1
          if (subsamplingX == 1) {
            subsamplingY = bitBuffer.getBits(index, 1)
            index += 1
          } else {
            subsamplingY = 0
          }
        } else {
          subsamplingX = 1
          subsamplingY = 0
        }
        if (subsamplingX == 1 && subsamplingY == 1) {
          samplePosition = bitBuffer.getBits(index, 1)
          index += 1
        }
      }
    }
    index += 1
    //finish config color
    index += 1
    val data = ByteBuffer.wrap(buffer, offset, size)
    data.put(0x81.toByte()) //marker and version
    data.put(((seqProfile shl 5) or seqLevelIdx).toByte())
    data.put(
      ((seqTier shl 7) or (highBitDepth shl 6) or (twelveBit shl 5) or (monochrome shl 4) or
      (subsamplingX shl 3) or (subsamplingY shl 2) or samplePosition).toByte()
    )
    val reserved = 0
    data.put(((reserved shl 5) or (initialDisplayDelayPresentFlag shl 4) or initialPresentationDelay).toByte())
    data.put(sequenceObu)
  }

  private fun readUVLC(byteArray: ByteArray, offset: Int): Pair<Int, Int> {
    var leadingZeros = 0
    var value = 0
    var currentIndex = offset / 8
    var currentBit = 7 - offset % 8

    while (byteArray[currentIndex].toInt() and (1 shl currentBit) == 0) {
      leadingZeros++
      if (currentBit == 0) {
        currentIndex++
        currentBit = 7
      } else {
        currentBit--
      }
    }

    for (i in 0 until leadingZeros + 1) {
      if (currentBit == 0) {
        currentIndex++
        currentBit = 7
      } else {
        currentBit--
      }

      value = (value shl 1) or ((byteArray[currentIndex].toInt() ushr currentBit) and 1)
    }

    return Pair(value, offset + leadingZeros + 1)
  }
}