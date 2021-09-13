/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.rtmp.utils

import android.util.Base64
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * Created by pedro on 27/04/21.
 */
object AuthUtil {

  fun getAdobeAuthUserResult(user: String, password: String, salt: String, challenge: String, opaque: String): String {
    val challenge2 = String.format("%08x", Random().nextInt())
    var response = stringToMd5Base64(user + salt + password)
    if (opaque.isNotEmpty()) {
      response += opaque
    } else if (challenge.isNotEmpty()) {
      response += challenge
    }
    response = stringToMd5Base64(response + challenge2)
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

  fun stringToMd5Base64(s: String): String {
    try {
      val md = MessageDigest.getInstance("MD5")
      md.update(s.toByteArray())
      val md5hash = md.digest()
      return Base64.encodeToString(md5hash, Base64.NO_WRAP)
    } catch (ignore: Exception) { }
    return ""
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

  fun getMd5Hash(buffer: String): String {
    val md: MessageDigest
    try {
      md = MessageDigest.getInstance("MD5")
      return bytesToHex(md.digest(buffer.toByteArray()))
    } catch (ignore: NoSuchAlgorithmException) {
    } catch (ignore: UnsupportedEncodingException) {
    }
    return ""
  }

  fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
  }
}