/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.whip.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SdpParserTest {

  private val sessionHeader = "v=0\r\n" +
      "o=- 1 2 IN IP4 0.0.0.0\r\n" +
      "s=-\r\n" +
      "t=0 0\r\n"
  private val mediaInfo = "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
      "c=IN IP4 0.0.0.0\r\n" +
      "a=ice-ufrag:94441946\r\n" +
      "a=ice-pwd:37b9579j5pr97o6a86sm205436991m8k\r\n" +
      "a=fingerprint:sha-256 D8:9F:3B:9D:05:F6:C2:84:6C:DD:DD:F2:C3:3A:21:EE:AD:9D:57:4B:A2:24:AC:88:A2:E0:1F:DF:95:70:21:A2\r\n"
  private val candidate = "a=candidate:0 1 udp 2130706431 10.0.2.2 8000 typ host generation 0\r\n"

  @Test
  fun `GIVEN ice-lite passive answer WHEN parse THEN get passive setup`() {
    val answer = sessionHeader +
        "a=ice-lite\r\n" +
        mediaInfo +
        "a=setup:passive\r\n" +
        "a=mid:0\r\n" +
        candidate
    val info = SdpParser.parseBodyAnswer(answer)
    assertEquals(false, info.isActiveMode)
    assertEquals("94441946", info.uFrag)
    assertEquals("37b9579j5pr97o6a86sm205436991m8k", info.uPass)
    assertEquals("D8:9F:3B:9D:05:F6:C2:84:6C:DD:DD:F2:C3:3A:21:EE:AD:9D:57:4B:A2:24:AC:88:A2:E0:1F:DF:95:70:21:A2", info.fingerprint)
    assertEquals(1, info.candidates.size)
  }

  @Test
  fun `GIVEN active answer WHEN parse THEN get active setup`() {
    val answer = sessionHeader + mediaInfo + "a=setup:active\r\n" + candidate
    val info = SdpParser.parseBodyAnswer(answer)
    assertEquals(true, info.isActiveMode)
  }

  @Test
  fun `GIVEN setup in session and media level WHEN parse THEN media level has priority`() {
    val answer = sessionHeader + "a=setup:active\r\n" + mediaInfo + "a=setup:passive\r\n" + candidate
    val info = SdpParser.parseBodyAnswer(answer)
    assertEquals(false, info.isActiveMode)
  }

  @Test
  fun `GIVEN answer without setup WHEN parse THEN setup is null`() {
    val answer = sessionHeader + mediaInfo + candidate
    val info = SdpParser.parseBodyAnswer(answer)
    assertFalse(info.isActiveMode)
  }
}
