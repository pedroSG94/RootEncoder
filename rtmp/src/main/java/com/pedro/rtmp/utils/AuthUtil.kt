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

package com.pedro.rtmp.utils

import com.pedro.common.getMd5Hash
import java.security.MessageDigest
import java.util.Random
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    val hash1 = "$user:$realm:$password".getMd5Hash()
    val hash2 = "$method:/$path".getMd5Hash()
    val hash3 = "$hash1:$nonce:$ncHex:$cNonce:$qop:$hash2".getMd5Hash()
    return "?authmod=$authMod&user=$user&nonce=$nonce&cnonce=$cNonce&nc=$ncHex&response=$hash3"
  }

  fun getSalt(description: String): String = findDescriptionValue("salt=", description)

  fun getChallenge(description: String): String = findDescriptionValue("challenge=", description)

  fun getOpaque(description: String): String = findDescriptionValue("opaque=", description)

  @OptIn(ExperimentalEncodingApi::class)
  fun stringToMd5Base64(s: String): String {
    try {
      val md = MessageDigest.getInstance("MD5")
      md.update(s.toByteArray())
      val md5hash = md.digest()
      return Base64.encode(md5hash)
    } catch (ignore: Exception) { }
    return ""
  }

  /**
   * Limelight auth utils
   */
  fun getNonce(description: String): String = findDescriptionValue("nonce=", description)

  private fun findDescriptionValue(value: String, description: String): String {
    val data = description.split("&").toTypedArray()
    for (s in data) {
      if (s.contains(value)) {
        return s.substring(value.length)
      }
    }
    return ""
  }
}