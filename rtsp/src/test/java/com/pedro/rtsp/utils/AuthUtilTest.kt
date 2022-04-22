package com.pedro.rtsp.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 14/4/22.
 */
class AuthUtilTest {

  @Test
  fun `GIVEN String WHEN generate hash THEN return a MD5 hash String`() {
    val fakeBuffer = "hello world"
    val expectedResult = "5eb63bbbe01eeed093cb22bb8f5acdc3"
    val md5Hash = AuthUtil.getMd5Hash(fakeBuffer)
    assertEquals(expectedResult, md5Hash)
  }
}