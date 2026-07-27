package com.pedro.common.av1

import com.pedro.common.BitBuffer
import com.pedro.common.toByteArray
import java.nio.ByteBuffer

class Av1SequenceHeaderParser {

  var seqProfile = 0
  var seqLevelIdx = 0
  var seqTier = false
  var highBitDepth = false
  var twelveBit = false
  var monochrome = false
  var subsamplingX = false
  var subsamplingY = false
  var samplePosition = 0
  var initialDisplayDelayPresentFlag = false
  var initialPresentationDelay = 0


  fun parse(sequenceObu: ByteBuffer) {
    parse(sequenceObu.toByteArray())
  }

  fun parse(sequenceObu: ByteArray) {
    val av1Parser = Av1Parser()
    val obuData = av1Parser.getObus(sequenceObu)[0].data
    val bitBuffer = BitBuffer(ByteBuffer.wrap(obuData))

    seqProfile = bitBuffer.getInt(3)
    bitBuffer.skipBool()
    val reducedStillPictureHeader = bitBuffer.getBool()
    initialDisplayDelayPresentFlag = false
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
      val seqForceScreenContentTools = seqChooseScreenContentTools || bitBuffer.getBool()
      if (seqForceScreenContentTools) {
        val seqChooseIntegerMv = bitBuffer.getBool()
        if (!seqChooseIntegerMv) bitBuffer.skipBool()
      }
      if (enableOrderHint) bitBuffer.skip(3)
    }
    bitBuffer.skip(3)
    //config color
    highBitDepth = bitBuffer.getBool()
    twelveBit = false
    var bitDepth = 0
    if (seqProfile == 2 && highBitDepth) {
      twelveBit = bitBuffer.getBool()
      bitDepth = if (twelveBit) 12 else 10
    } else if (seqProfile <= 2) {
      bitDepth = if (highBitDepth) 10 else 8
    }
    monochrome = if (seqProfile == 1) {
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
    samplePosition = 0
    if (monochrome) {
      bitBuffer.getBool()
      subsamplingX = true
      subsamplingY = true
    } else if (colorPrimaries == 1 && transferCharacteristics == 13 && matrixCoefficients == 0) {
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
      }
      if (subsamplingX && subsamplingY) {
        samplePosition = bitBuffer.getInt(2)
      }
    }
  }
}
