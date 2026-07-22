package com.pedro.rtmp.flv.video.config

import com.pedro.common.BitBuffer
import java.nio.ByteBuffer

/**
 * aligned(8) class VPCodecConfigurationRecord {
 *     unsigned int(8)  profile;
 *     unsigned int(8)  level;
 *     unsigned int(4)  bitDepth;
 *     unsigned int(3)  chromaSubsampling;
 *     unsigned int(1)  videoFullRangeFlag;
 *     unsigned int(8)  colourPrimaries;
 *     unsigned int(8)  transferCharacteristics;
 *     unsigned int(8)  matrixCoefficients;
 *     unsigned int(16) codecInitializationDataSize;
 * }
 */
class VideoSpecificConfigVp9(
  private val info: ByteArray
) {

  val size: Int = 12

  fun write(buffer: ByteArray, offset: Int) {
    val bitBuffer = BitBuffer(ByteBuffer.wrap(info))
    bitBuffer.skip(2)
    val profile = bitBuffer.getInt(1) or (bitBuffer.getInt(1) shl 1)
    if (profile == 3) bitBuffer.skip(1)
    bitBuffer.skip(1)
    bitBuffer.skip(1) //is keyframe
    bitBuffer.skip(2)
    bitBuffer.skip(24)

    var bitDepth = 8
    if (profile >= 2) bitDepth = if (bitBuffer.getInt(1) == 1) 12 else 10
    val colorSpace = bitBuffer.getInt(3)        // 0=UNKNOWN,1=601,2=709,3=170M,4=240M,5=2020,7=RGB
    val fullRange: Int
    var subX = 1
    var subY = 1
    if (colorSpace != 7) {              // != CS_RGB
      fullRange = bitBuffer.getInt(1)
      if (profile == 1 || profile == 3) {
        subX = bitBuffer.getInt(1)
        subY = bitBuffer.getInt(1)
        bitBuffer.skip(1)
      }
      // profile 0/2 => 4:2:0 fijo
    } else {
      fullRange = 1
      if (profile == 1 || profile == 3) {
        subX = 0
        subY = 0
        bitBuffer.skip(1)
      }
    }
    val chroma = when {                 // -> chromaSubsampling
      subX == 1 && subY == 1 -> 1     // 4:2:0 (VP9 no señaliza co-siting; 0 o 1 por convención)
      subX == 1 && subY == 0 -> 2     // 4:2:2
      else -> 3     // 4:4:4
    }
    val width = bitBuffer.getInt(16) + 1   // frame_width_minus_1
    val height = bitBuffer.getInt(16) + 1  // frame_height_minus_1

    val color = Vp9ColorSpace.fromValue(colorSpace)
    val level = Vp9Level.getLevel(width, height).value
    val config = byteArrayOf(
      0x01, 0x00, 0x00, 0x00, // version = 1, flags = 0
      profile.toByte(),
      level.toByte(),
      ((bitDepth shl 4) or (chroma shl 1) or fullRange).toByte(),
      color.colourPrimaries.toByte(),
      color.getTransferCharacteristics(bitDepth).toByte(),
      color.matrixCoefficients.toByte(),
      0x00, 0x00 // codecInitializationDataSize
    )
    config.copyInto(buffer, offset)
  }
}