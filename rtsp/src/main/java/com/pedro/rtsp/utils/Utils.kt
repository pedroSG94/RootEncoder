package com.pedro.rtsp.utils

import java.nio.ByteBuffer

fun ByteArray.setLong(n: Long, begin: Int, end: Int) {
  var value = n
  for (i in end - 1 downTo begin step 1) {
    this[i] = (value % 256).toByte()
    value = value shr 8
  }
}

fun ByteBuffer.getVideoStartCodeSize(): Int {
  var startCodeSize = 0
  if (this.get(0).toInt() == 0x00 && this.get(1).toInt() == 0x00
    && this.get(2).toInt() == 0x00 && this.get(3).toInt() == 0x01) {
    //match 00 00 00 01
    startCodeSize = 4
  } else if (this.get(0).toInt() == 0x00 && this.get(1).toInt() == 0x00
    && this.get(2).toInt() == 0x01) {
    //match 00 00 01
    startCodeSize = 3
  }
  return startCodeSize
}