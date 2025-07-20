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

import com.pedro.common.readUInt16
import com.pedro.common.readUInt32
import com.pedro.common.readUntil
import com.pedro.common.toByteArray
import com.pedro.common.toUInt16
import com.pedro.common.toUInt32
import com.pedro.common.xorBytes
import com.pedro.whip.dtls.CryptoUtils
import com.pedro.whip.utils.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.util.zip.CRC32

/**
 * Created by pedro on 15/7/25.
 */
object StunAttributeValueParser {

  fun createUserName(localUfrag: String, remoteUfrag: String): ByteArray {
    val bytes = "$remoteUfrag:$localUfrag".toByteArray(Charsets.UTF_8)
    val padding = (4 - (bytes.size % 4)) % 4
    return bytes.plus(ByteArray(padding))
  }

  fun createSoftware(): ByteArray {
//    val bytes = "RootEncoder".toByteArray(Charsets.UTF_8)
    val bytes = "|pipe| webRTC agent for IoT https://pi.pe".toByteArray(Charsets.UTF_8)
    val padding = (4 - (bytes.size % 4)) % 4
    return bytes.plus(ByteArray(padding))
  }

  fun createXorMappedAddress(
    id: ByteArray, host: String, port: Int, isIpv4: Boolean
  ): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(0)
    output.write(if (isIpv4) 0x01 else 0x02)
    output.write(port.toUInt16().xorBytes(Constants.MAGIC_COOKIE.toUInt32()))
    val address = InetAddress.getByName(host)
    if (isIpv4) {
      output.write(address.address.xorBytes(Constants.MAGIC_COOKIE.toUInt32()))
    } else {
      val xor = Constants.MAGIC_COOKIE.toUInt32() + id
      output.write(address.address.xorBytes(xor))
    }
    return output.toByteArray()
  }

  fun readXorMappedAddress(bytes: ByteArray, id: ByteArray): String {
    val input = ByteArrayInputStream(bytes)
    input.read()
    val isIpv4 = input.read() == 0x01
    val port = input.readUInt16().toUInt16().xorBytes(Constants.MAGIC_COOKIE.toUInt32()).toUInt16()
    val xor = if (isIpv4) Constants.MAGIC_COOKIE.toUInt32() else Constants.MAGIC_COOKIE.toUInt32() + id
    val hostXor = ByteArray(if (isIpv4) 4 else 16)
    input.readUntil(hostXor)
    val host = hostXor.xorBytes(xor)
    val address = InetAddress.getByAddress(host)
    return "${address.hostAddress}:$port"
  }

  fun createMessageIntegrity(bytes: ByteArray, password: String): ByteArray {
    return CryptoUtils.calculateHmacSha1(bytes, password.toByteArray(Charsets.UTF_8))
  }

  fun createFingerprint(bytes: ByteArray): ByteArray {
    val crc32 = CRC32()
    crc32.update(bytes)
    return (crc32.value xor Constants.STUN_HEX).toInt().toUInt32()
  }
}