package com.pedro.srt.utils

import com.pedro.srt.srt.packets.control.handshake.EncryptionType
import com.pedro.srt.srt.packets.control.handshake.extension.CipherType
import com.pedro.srt.srt.packets.control.handshake.extension.EncryptInfo
import com.pedro.srt.srt.packets.data.KeyBasedEncryption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by pedro on 12/11/23.
 * Need API 26+
 *
 */
class EncryptionUtil(val type: EncryptionType, passphrase: String) {

  private val cipher: Cipher
  private val iterations = 2048 //reduce the number for performance but this make it less secure
  private var salt = ByteArray(16) //this is the only salt size accepted by SRT
  private var keyData = byteArrayOf()

  init {
    val keyLength = when (type) {
      EncryptionType.NONE -> 0
      EncryptionType.AES128 -> 128
      EncryptionType.AES192 -> 192
      EncryptionType.AES256 -> 256
    }
    cipher = Cipher.getInstance("AES/CTR/NoPadding")
    salt = generateRandomBytes(16)
    val sek = generateRandomBytes(32)
    val kek = generatePBKDF2Key(passphrase, salt, iterations, keyLength)

    val secretKeySpec = SecretKeySpec(kek, "AES")
    cipher.init(Cipher.WRAP_MODE, secretKeySpec)
    keyData = cipher.wrap(SecretKeySpec(sek, "AES"))
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
  }

  fun encrypt(bytes: ByteArray): ByteArray {
    return cipher.doFinal(bytes)
  }

  fun encrypt(bytes: ByteArray, offset: Int, length: Int): ByteArray {
    return cipher.doFinal(bytes, offset, length)
  }

  fun getEncryptInfo(): EncryptInfo {
    return EncryptInfo(
      keyBasedEncryption = KeyBasedEncryption.PAIR_KEY,
      cipher = CipherType.CTR,
      salt = salt,
      key = keyData
    )
  }

  private fun generateRandomBytes(length: Int): ByteArray {
    val secureRandom = SecureRandom()
    val randomBytes = ByteArray(length)
    secureRandom.nextBytes(randomBytes)
    return randomBytes
  }

  private fun generatePBKDF2Key(passphrase: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyLength)
    val tmp = factory.generateSecret(spec)
    return tmp.encoded
  }
}