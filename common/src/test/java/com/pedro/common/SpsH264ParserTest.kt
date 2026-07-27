package com.pedro.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 27/07/26.
 */
class SpsH264ParserTest {

  @Test
  fun `GIVEN a real H264 high profile sps WHEN parse THEN decode the expected fields`() {
    //high profile level 3.0 sps
    val sps = byteArrayOf(
      103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8
    )
    val parser = SpsH264Parser()
    parser.parse(sps)

    assertEquals(100, parser.profileIdc)
    assertEquals(0, parser.profileCompatibility)
    assertEquals(30, parser.levelIdc)
    assertEquals(1, parser.chromaFormat)
    assertEquals(0, parser.bitDepthLumaMinus8)
    assertEquals(0, parser.bitDepthChromaMinus8)
  }

  @Test
  fun `GIVEN a constrained baseline sps WHEN parse THEN keep the inferred chroma values`() {
    //constrained baseline level 3.1 sps, it doesn't contain chroma_format_idc neither the bit depths
    val sps = byteArrayOf(
      103, 66, -64, 31, -39, 0, -16, 17, 126, -16, 17, 0, 0, 3, 0, 1, 0, 0, 3, 0, 50, 15, 22, 46, 72
    )
    val parser = SpsH264Parser()
    parser.parse(sps)

    assertEquals(66, parser.profileIdc)
    assertEquals(192, parser.profileCompatibility)
    assertEquals(31, parser.levelIdc)
    //inferred values because the sps doesn't contain them
    assertEquals(1, parser.chromaFormat)
    assertEquals(0, parser.bitDepthLumaMinus8)
    assertEquals(0, parser.bitDepthChromaMinus8)
  }
}
