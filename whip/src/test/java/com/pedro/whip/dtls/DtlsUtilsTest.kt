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

package com.pedro.whip.dtls

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DtlsUtilsTest {

  //RFC 3711 B.3 key derivation test vectors
  private val masterKey = byteArrayOf(0xE1.toByte(), 0xF9.toByte(), 0x7A, 0x0D, 0x3E, 0x01, 0x8B.toByte(), 0xE0.toByte(), 0xD6.toByte(), 0x4F, 0xA3.toByte(), 0x2C, 0x06, 0xDE.toByte(), 0x41, 0x39)
  private val masterSalt = byteArrayOf(0x0E, 0xC6.toByte(), 0x75, 0xAD.toByte(), 0x49, 0x8A.toByte(), 0xFE.toByte(), 0xEB.toByte(), 0xB6.toByte(), 0x96.toByte(), 0x0B, 0x3A, 0xAB.toByte(), 0xE6.toByte())
  private val rtpCipherKey = byteArrayOf(0xC6.toByte(), 0x1E, 0x7A, 0x93.toByte(), 0x74, 0x4F, 0x39, 0xEE.toByte(), 0x10, 0x73, 0x4A, 0xFE.toByte(), 0x3F, 0xF7.toByte(), 0xA0.toByte(), 0x87.toByte())
  private val rtpSalt = byteArrayOf(0x30, 0xCB.toByte(), 0xBC.toByte(), 0x08, 0x86.toByte(), 0x3D, 0x8C.toByte(), 0x85.toByte(), 0xD4.toByte(), 0x9D.toByte(), 0xB3.toByte(), 0x4A, 0x9A.toByte(), 0xE1.toByte())
  private val rtpAuthKey = byteArrayOf(0xCE.toByte(), 0xBE.toByte(), 0x32, 0x1F, 0x6F, 0xF7.toByte(), 0x71, 0x6B, 0x6F, 0xD4.toByte(), 0xAB.toByte(), 0x49, 0xAF.toByte(), 0x25, 0x6A, 0x15, 0x6D, 0x38, 0xBA.toByte(), 0xA4.toByte())
  //same master key and salt with the SRTCP labels 0x03, 0x04 and 0x05 (RFC 3711 4.3.2), calculated with openssl
  private val rtcpCipherKey = byteArrayOf(0x4C, 0x1A, 0xA4.toByte(), 0x5A, 0x81.toByte(), 0xF7.toByte(), 0x3D, 0x61, 0xC8.toByte(), 0x00, 0xBB.toByte(), 0xB0.toByte(), 0x0F, 0xBB.toByte(), 0x1E, 0xAA.toByte())
  private val rtcpSalt = byteArrayOf(0x95.toByte(), 0x81.toByte(), 0xC7.toByte(), 0xAD.toByte(), 0x87.toByte(), 0xB3.toByte(), 0xE5.toByte(), 0x30, 0xBF.toByte(), 0x3E, 0x44, 0x54, 0xA8.toByte(), 0xB3.toByte())
  private val rtcpAuthKey = byteArrayOf(0x8D.toByte(), 0x54, 0x53, 0x4F, 0xEB.toByte(), 0x49, 0xAE.toByte(), 0x8E.toByte(), 0x79, 0x93.toByte(), 0xA6.toByte(), 0xBD.toByte(), 0x0B, 0x84.toByte(), 0x4F, 0xC3.toByte(), 0x23, 0xA9.toByte(), 0x3D, 0xFD.toByte())
  //master key and salt of the other side
  private val otherKey = byteArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
  private val otherSalt = byteArrayOf(0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22)

  @Test
  fun `GIVEN exported keys WHEN derive keys as client THEN use client master key and salt`() {
    //[clientMasterKey | serverMasterKey | clientMasterSalt | serverMasterSalt]
    val exportedKeys = masterKey + otherKey + masterSalt + otherSalt
    val keys = DtlsUtils.deriveSrtpKeys(exportedKeys, isClient = true)
    assertArrayEquals(rtpCipherKey, keys.rtp.sessionKey)
    assertArrayEquals(rtpAuthKey, keys.rtp.authKey)
    assertArrayEquals(rtpSalt, keys.rtp.salt)
    assertArrayEquals(rtcpCipherKey, keys.rtcp.sessionKey)
    assertArrayEquals(rtcpAuthKey, keys.rtcp.authKey)
    assertArrayEquals(rtcpSalt, keys.rtcp.salt)
  }

  @Test
  fun `GIVEN exported keys WHEN derive keys as server THEN use server master key and salt`() {
    //[clientMasterKey | serverMasterKey | clientMasterSalt | serverMasterSalt]
    val exportedKeys = otherKey + masterKey + otherSalt + masterSalt
    val keys = DtlsUtils.deriveSrtpKeys(exportedKeys, isClient = false)
    assertArrayEquals(rtpCipherKey, keys.rtp.sessionKey)
    assertArrayEquals(rtpAuthKey, keys.rtp.authKey)
    assertArrayEquals(rtpSalt, keys.rtp.salt)
    assertArrayEquals(rtcpCipherKey, keys.rtcp.sessionKey)
    assertArrayEquals(rtcpAuthKey, keys.rtcp.authKey)
    assertArrayEquals(rtcpSalt, keys.rtcp.salt)
  }
}
