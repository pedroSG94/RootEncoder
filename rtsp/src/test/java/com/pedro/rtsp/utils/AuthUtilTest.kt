/*
 * Copyright (C) 2023 pedroSG94.
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