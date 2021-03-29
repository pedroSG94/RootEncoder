package com.pedro.rtsp.utils

fun ByteArray.setLong(n: Long, begin: Int, end: Int) {
  var value = n
  for (i in end - 1 downTo begin step 1) {
    this[i] = (value % 256).toByte()
    value = value shr 8
  }
}