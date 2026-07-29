package com.pedro.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 27/07/26.
 */
class SpsH265ParserTest {

  //real main profile level 5.1 sps, sps_max_sub_layers_minus1 = 0
  private val spsSingleLayer = byteArrayOf(
    66, 1, 1, 1, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -103, -96, 15, 8, 2, -127,
    104, -76, -82, -55, 46, -26, -96, -64, -64, -64, 16
  )

  //1920x1080 sps with sps_max_sub_layers_minus1 = 1, so profile_tier_level carries the
  //sub layer flags plus the reserved_zero_2bits padding
  private val spsSubLayers = byteArrayOf(
    66, 1, 3, 1, 96, 0, 0, 0, 0, 0, 0, 0, 0, 0, 123, 0, 0, -96, 3, -64, -128, 16, -27, -128
  )

  @Test
  fun `GIVEN a real H265 sps WHEN parse THEN decode the profile_tier_level fields`() {
    val parser = SpsH265Parser()
    parser.parse(spsSingleLayer)

    assertEquals(0, parser.generalProfileSpace)
    assertEquals(0, parser.generalTierFlag)
    assertEquals(1, parser.generalProfileIdc)
    assertEquals(0x60000000, parser.generalProfileCompatibilityFlags)
    assertEquals(0L, parser.generalConstraintIndicatorFlags)
    assertEquals(153, parser.generalLevelIdc)
    assertEquals(1, parser.chromaFormat)
    assertEquals(0, parser.bitDepthLumaMinus8)
    assertEquals(0, parser.bitDepthChromaMinus8)
  }

  @Test
  fun `GIVEN a sps with temporal sub layers WHEN parse THEN keep the bitstream aligned`() {
    val parser = SpsH265Parser()
    parser.parse(spsSubLayers)

    assertEquals(0, parser.generalProfileSpace)
    assertEquals(0, parser.generalTierFlag)
    assertEquals(1, parser.generalProfileIdc)
    assertEquals(0x60000000, parser.generalProfileCompatibilityFlags)
    assertEquals(0L, parser.generalConstraintIndicatorFlags)
    assertEquals(123, parser.generalLevelIdc)
    //these are the ones that desync if the reserved_zero_2bits loop reads an extra iteration
    assertEquals(1, parser.chromaFormat)
    assertEquals(0, parser.bitDepthLumaMinus8)
    assertEquals(0, parser.bitDepthChromaMinus8)
  }

  @Test
  fun `GIVEN a sps with start code WHEN parse THEN get the same result than without it`() {
    val withStartCode = byteArrayOf(0, 0, 0, 1).plus(spsSingleLayer)
    val parser = SpsH265Parser()
    parser.parse(ByteBuffer.wrap(withStartCode))

    assertEquals(1, parser.generalProfileIdc)
    assertEquals(153, parser.generalLevelIdc)
    assertEquals(1, parser.chromaFormat)
    assertEquals(0, parser.bitDepthLumaMinus8)
    assertEquals(0, parser.bitDepthChromaMinus8)
  }
}
