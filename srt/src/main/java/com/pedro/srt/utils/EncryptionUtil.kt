/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.srt.utils

import com.pedro.srt.srt.packets.control.handshake.EncryptionType
import com.pedro.srt.srt.packets.control.handshake.extension.CipherType
import com.pedro.srt.srt.packets.data.KeyBasedEncryption
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * Created by pedro on 12/11/23.
 * Need API 26+
 *
 */
class EncryptionUtil(val type: EncryptionType, passphrase: String) {

  //only pair is developed, odd is not supported for now
  private val keyBasedEncryption = KeyBasedEncryption.PAIR_KEY
  private val cipherType = CipherType.CTR
  private val salt: ByteArray
  private val keyLength: Int = when (type) {
    EncryptionType.NONE -> 0
    EncryptionType.AES128 -> 16
    EncryptionType.AES192 -> 24
    EncryptionType.AES256 -> 32
  }
  private var keyData = byteArrayOf()
  private val block: SecretKeySpec
  private val cipher = Cipher.getInstance("AES/CTR/NoPadding")

  init {
    salt = generateSecureRandomBytes(16)
    val sek = generateSecureRandomBytes(keyLength)
    val kek = calculateKEK(passphrase, salt, keyLength)
    keyData = wrapKey(kek, sek)
    block = SecretKeySpec(sek, "AES")
  }

  fun encrypt(bytes: ByteArray, sequence: Int): ByteArray {
    val ctr = ByteArray(16)
    ByteBuffer.wrap(ctr, 10, 4).putInt(sequence)
    for (i in 0 until 14) ctr[i] = (ctr[i] xor salt[i])

    cipher.init(Cipher.ENCRYPT_MODE, block, IvParameterSpec(ctr))
    return cipher.doFinal(bytes)
  }

  fun getEncryptInfo(): EncryptInfo {
    return EncryptInfo(
      keyBasedEncryption = keyBasedEncryption,
      cipher = cipherType,
      salt = salt,
      key = keyData,
      keyLength = keyLength
    )
  }

  private fun generateSecureRandomBytes(length: Int): ByteArray {
    val secureRandom = SecureRandom()
    val randomBytes = ByteArray(length)
    secureRandom.nextBytes(randomBytes)
    return randomBytes
  }

  /**
   * Wrap key RFC 3394
   */
  private fun wrapKey(kek: ByteArray, keyToWrap: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AESWrap")
    val secretKek = SecretKeySpec(kek, "AES")
    cipher.init(Cipher.WRAP_MODE, secretKek)
    val secret = SecretKeySpec(keyToWrap, "AES")
    return cipher.wrap(secret)
  }

  /**
   * generate Pbkdf2 key
   */
  private fun calculateKEK(passphrase: String, salt: ByteArray, keyLength: Int): ByteArray {
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(passphrase.toCharArray(), salt.sliceArray(8 until salt.size), 2048, keyLength * 8)).encoded
  }
}