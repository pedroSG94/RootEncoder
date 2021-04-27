package com.pedro.rtmp.utils

import android.util.Base64
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.experimental.and

/**
 * Created by pedro on 27/04/21.
 */
object AuthUtil {

  fun getAdobeAuthUserResult(user: String, password: String, salt: String, challenge: String, opaque: String): String {
    val challenge2 = String.format("%08x", Random().nextInt())
    var response = stringToMD5BASE64(user + salt + password)
    if (opaque.isNotEmpty()) {
      response += opaque
    } else if (challenge.isNotEmpty()) {
      response += challenge
    }
    response = stringToMD5BASE64(response + challenge2)
    var result = "?authmod=adobe&user=$user&challenge=$challenge2&response=$response"
    if (opaque.isNotEmpty()) {
      result += "&opaque=$opaque"
    }
    return result
  }

  /**
   * Limelight auth. This auth is closely to Digest auth
   * http://tools.ietf.org/html/rfc2617
   * http://en.wikipedia.org/wiki/Digest_access_authentication
   *
   * https://github.com/ossrs/librtmp/blob/feature/srs/librtmp/rtmp.c
   */
  fun getLlnwAuthUserResult(user: String, password: String, nonce: String, app: String): String {
    val authMod = "llnw"
    val realm = "live"
    val method = "publish"
    val qop = "auth"
    val ncHex = String.format("%08x", 1)
    val cNonce = String.format("%08x", Random().nextInt())
    var path = app
    //extract query parameters
    val queryPos = path.indexOf("?")
    if (queryPos >= 0) path = path.substring(0, queryPos)
    if (!path.contains("/")) path += "/_definst_"
    val hash1 = getMd5Hash("$user:$realm:$password")
    val hash2 = getMd5Hash("$method:/$path")
    val hash3 = getMd5Hash("$hash1:$nonce:$ncHex:$cNonce:$qop:$hash2")
    return "?authmod=$authMod&user=$user&nonce=$nonce&cnonce=$cNonce&nc=$ncHex&response=$hash3"
  }

  fun getSalt(description: String): String {
    var salt = ""
    val data = description.split("&").toTypedArray()
    for (s in data) {
      if (s.contains("salt=")) {
        salt = s.substring(5)
        break
      }
    }
    return salt
  }

  fun getChallenge(description: String): String {
    var challenge = ""
    val data = description.split("&").toTypedArray()
    for (s in data) {
      if (s.contains("challenge=")) {
        challenge = s.substring(10)
        break
      }
    }
    return challenge
  }

  fun getOpaque(description: String): String {
    var opaque = ""
    val data = description.split("&").toTypedArray()
    for (s in data) {
      if (s.contains("opaque=")) {
        opaque = s.substring(7)
        break
      }
    }
    return opaque
  }

  fun stringToMD5BASE64(s: String): String? {
    return try {
      val md = MessageDigest.getInstance("MD5")
      md.update(s.toByteArray(charset("UTF-8")), 0, s.length)
      val md5hash = md.digest()
      Base64.encodeToString(md5hash, Base64.NO_WRAP)
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Limelight auth utils
   */
  fun getNonce(description: String): String {
    var nonce = ""
    val data = description.split("&").toTypedArray()
    for (s in data) {
      if (s.contains("nonce=")) {
        nonce = s.substring(6)
        break
      }
    }
    return nonce
  }

  private val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  private fun getMd5Hash(buffer: String): String {
    val md: MessageDigest
    try {
      md = MessageDigest.getInstance("MD5")
      return bytesToHex(md.digest(buffer.toByteArray(charset("UTF-8"))))
    } catch (ignore: NoSuchAlgorithmException) {
    } catch (ignore: UnsupportedEncodingException) {
    }
    return ""
  }

  private fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    var v: Int
    for (j in bytes.indices) {
      v = (bytes[j] and 0xFF.toByte()).toInt()
      hexChars[j * 2] = hexArray[v ushr 4]
      hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
  }
}