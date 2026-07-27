package com.pedro.common

import java.nio.ByteBuffer

/**
 * Created by pedro on 27/07/26.
 *
 * ISO/IEC 14496-10 7.3.2.1.1
 *
 */
class SpsH264Parser {
  var profileIdc = 0
  var profileCompatibility = 0
  var levelIdc = 0
  //only present in the profiles of profilesWithChromaInfo, in other case the inferred values are used
  var chromaFormat = 1
  var bitDepthLumaMinus8 = 0
  var bitDepthChromaMinus8 = 0

  fun parse(sps: ByteArray) {
    parse(ByteBuffer.wrap(sps))
  }

  fun parse(sps: ByteBuffer) {
    val rbsp = BitBuffer.extractRbsp(ByteBuffer.wrap(sps.getData()), 1)
    val bitBuffer = BitBuffer(rbsp)
    bitBuffer.skip(8)
    //profile_idc
    profileIdc = bitBuffer.getInt(8)
    //constraint_set0_flag to constraint_set5_flag and reserved_zero_2bits
    profileCompatibility = bitBuffer.getInt(8)
    //level_idc
    levelIdc = bitBuffer.getInt(8)
    //seq_parameter_set_id
    bitBuffer.readUE()
    if (profileIdc in profilesWithChromaInfo) {
      //chroma_format_idc
      chromaFormat = bitBuffer.readUE()
      if (chromaFormat == 3) {
        //separate_colour_plane_flag
        bitBuffer.skipBool()
      }
      //bit_depth_luma_minus8
      bitDepthLumaMinus8 = bitBuffer.readUE()
      //bit_depth_chroma_minus8
      bitDepthChromaMinus8 = bitBuffer.readUE()
    }
    //The buffer continue but we don't need read more
  }

  companion object {
    private val profilesWithChromaInfo = listOf(
      100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135
    )
  }
}
