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

package com.pedro.whip.webrtc.stun

import com.pedro.common.CRC32
import com.pedro.common.toByteArray
import com.pedro.common.toUInt16
import com.pedro.common.toUInt32
import com.pedro.common.xorBytes
import com.pedro.whip.dtls.CryptoUtils
import com.pedro.whip.utils.Constants
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Created by pedro on 15/7/25.
 */
object StunAttributeValueParser {

  fun createUserName(localUfrag: String, remoteUfrag: String): ByteArray {
    return "$remoteUfrag:$localUfrag".toByteArray(Charsets.UTF_8)
  }

  fun createXorMappedAddress(
    id: BigInteger, host: String, port: Int, isIpv4: Boolean
  ): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(0)
    output.write(if (isIpv4) 0x01 else 0x02)
    output.write(port.toUInt16().xorBytes(Constants.MAGIC_COOKIE.toUInt32()))
    val address = InetAddress.getByName(host)
    if (isIpv4) {
      output.write(address.address.xorBytes(Constants.MAGIC_COOKIE.toUInt32()))
    } else {
      val xor = Constants.MAGIC_COOKIE.toUInt32() + id.toByteArray(12)
      output.write(address.address.xorBytes(xor))
    }
    return output.toByteArray()
  }

  fun createMessageIntegrity(bytes: ByteArray, password: String): ByteArray {
    return CryptoUtils.calculateHmacSha1(bytes, password)
  }

  fun createFingerprint(bytes: ByteArray): ByteArray {
    return CRC32.getCRC32(bytes).toUInt32().xorBytes(Constants.STUN_HEX.toUInt32())
  }
}