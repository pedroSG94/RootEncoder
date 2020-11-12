package com.pedro.rtsp.utils

import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.experimental.and

/**
 * Created by pedro on 22/02/17.
 */
internal object AuthUtil {

  private val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  @JvmStatic
  fun getMd5Hash(buffer: String): String {
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
      hexChars[j * 2] = hexArray[(v).ushr(4)]
      hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
  }
}