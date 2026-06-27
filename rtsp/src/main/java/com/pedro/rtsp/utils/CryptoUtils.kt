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

package com.pedro.rtsp.utils

import com.pedro.common.toUInt32
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * Created by pedro on 11/7/25.
 *
 * Crypto class used to encrypt in SRTP implementation
 * RFC 3711
 */
class CryptoUtils(
  private val cryptoProperties: CryptoProperties
) {

  private val mac = Mac.getInstance("HmacSHA1").apply {
    init(SecretKeySpec(cryptoProperties.authKey, "HmacSHA1"))
  }

  // AES/ECB is used to implement AES-CM (RFC 3711 §4.1.1).
  // Java's AES/CTR increments the counter at B[15], but AES-CM XORs j*2^16 at B[13],
  // so they diverge after the first 16-byte block. We implement AES-CM manually.
  private val ecbCipher = Cipher.getInstance("AES/ECB/NoPadding")
  private val aesKey = SecretKeySpec(cryptoProperties.sessionKey, "AES")

  init {
    ecbCipher.init(Cipher.ENCRYPT_MODE, aesKey)
  }

  fun encrypt(buffer: ByteArray, ivData: ByteArray): ByteArray {
    // RFC 3711 4.1.1 AES Counter Mode: keystream_j = E(k, (IV + j) mod 2^128)
    // The SRTP IV has its low 16 bits set to zero, so the block counter j occupies
    // bits 15..0 → bytes 14..15 in a 16-byte big-endian block
    val result = ByteArray(buffer.size)
    val block = ByteArray(16)
    var offset = 0
    var j = 0
    while (offset < buffer.size) {
      ivData.copyInto(block)
      block[15] = (block[15].toInt() xor (j and 0xFF)).toByte()
      block[14] = (block[14].toInt() xor ((j shr 8) and 0xFF)).toByte()
      val keystream = ecbCipher.doFinal(block)
      val toCopy = minOf(16, buffer.size - offset)
      for (i in 0 until toCopy) result[offset + i] = buffer[offset + i] xor keystream[i]
      offset += 16
      j++
    }
    return result
  }

  fun calculateHmac(buffer: ByteArray, roc: Int): ByteArray {
    mac.reset()
    mac.update(buffer)
    mac.update(roc.toUInt32())
    return mac.doFinal().copyOf(RtpConstants.HMAC_SIZE)
  }

  fun generateIv(
    ssrc: Long,
    index: Long
  ): ByteArray {
    val ivBase = ByteArray(16)
    ByteBuffer.wrap(ivBase, 4, 4).putInt(ssrc.toInt())
    val indexBytes = ByteBuffer.allocate(8).putLong(index).array()
    System.arraycopy(indexBytes, 2, ivBase, 8, 6)

    val paddedSalt = ByteArray(16).apply { cryptoProperties.salt.copyInto(this) }
    return ByteArray(16).apply {
      for (i in this.indices) this[i] = ivBase[i] xor paddedSalt[i]
    }
  }
}