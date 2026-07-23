package com.pedro.rtmp.flv.video.config

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
class VideoSpecificConfigVp8 {

  val size: Int = 12

  fun write(buffer: ByteArray, offset: Int) {
    //vp8 is always the same. We should hardcode it.
    val config = byteArrayOf(
      0x01, 0x00, 0x00, 0x00,   // version = 1, flags = 0
      0x00,                     // profile = 0
      0x00,                     // level = 0
      0x82.toByte(),            // bitDepth 8 | chroma 4:2:0 | fullRange 0
      0x02,                     // colourPrimaries = 2
      0x02,                     // transferCharacteristics = 2
      0x02,                     // matrixCoefficients = 2
      0x00, 0x00
    )
    config.copyInto(buffer, offset)
  }
}