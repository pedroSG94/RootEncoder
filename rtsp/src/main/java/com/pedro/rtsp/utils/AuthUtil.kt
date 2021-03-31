package com.pedro.rtsp.utils

import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object AuthUtil {

  @JvmStatic
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

  private fun bytesToHex(raw: ByteArray): String {
    return raw.joinToString("") { "%02x".format(it) }
  }
}