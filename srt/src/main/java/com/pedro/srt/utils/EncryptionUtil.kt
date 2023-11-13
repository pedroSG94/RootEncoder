package com.pedro.srt.utils

import android.util.Log
import com.pedro.srt.srt.packets.control.handshake.EncryptionType
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Created by pedro on 12/11/23.
 * Need API 26+
 *
 */
class EncryptionUtil(val type: EncryptionType, passphrase: String) {

  private val cipher: Cipher
  private val iterations = 10_000 //reduce the number for performance but this make it less secure

  init {
    val keyLength = when (type) {
      EncryptionType.NONE -> 0
      EncryptionType.AES128 -> 128
      EncryptionType.AES192 -> 192
      EncryptionType.AES256 -> 256
    }
    cipher = Cipher.getInstance("AES_$keyLength/CBC/PKCS5PADDING")
    val salt = ByteArray(128)
    val secureRandom = SecureRandom()
    secureRandom.nextBytes(salt)

    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyLength)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val key = factory.generateSecret(spec)
    cipher.init(Cipher.ENCRYPT_MODE, key)
  }

  fun encrypt(bytes: ByteArray): ByteArray {
    return cipher.doFinal(bytes)
  }

  fun encrypt(bytes: ByteArray, offset: Int, length: Int): ByteArray {
    return cipher.doFinal(bytes, offset, length)
  }
}