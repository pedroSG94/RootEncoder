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
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by pedro on 11/7/25.
 */
class CryptoUtils(
  private val cryptoProperties: CryptoProperties
) {

  private val mac = Mac.getInstance("HmacSHA1").apply {
    init(SecretKeySpec(cryptoProperties.authKey, "HmacSHA1"))
  }

  private val cipher = Cipher.getInstance("AES/CTR/NoPadding")
  private val aesKey = SecretKeySpec(cryptoProperties.sessionKey, "AES")

  fun encrypt(buffer: ByteArray, ssrc: Long, seq: Long, roc: Int): ByteArray {
    val ivData = ByteArray(16)
    ivData.setLong(ssrc, 4, 8)
    ivData.setLong(roc.toLong(), 8, 12)
    ivData.setLong(seq, 12, 14)

    cipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(ivData))
    cipher.update(buffer)
    return cipher.doFinal()
  }

  fun calculateHmac(buffer: ByteArray, roc: Int): ByteArray {
    mac.reset()
    mac.update(buffer)
    mac.update(roc.toUInt32())
    return mac.doFinal().copyOf(RtpConstants.HMAC_SIZE)
  }
}