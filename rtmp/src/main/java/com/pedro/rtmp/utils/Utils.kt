package com.pedro.rtmp.utils

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */

fun InputStream.readUntil(byteArray: ByteArray) {
  var bytesRead = 0
  while (bytesRead < byteArray.size) {
    val result = read(byteArray, bytesRead, byteArray.size - bytesRead)
    if (result != -1) bytesRead += result
  }
}

fun InputStream.readUInt32(): Int {
  return read() and 0xff shl 24 or read() and 0xff shl 16 or read() and 0xff shl 8 or read() and 0xff
}

fun InputStream.readUInt24(): Int {
  return read() and 0xff shl 16 or read() and 0xff shl 8 or read() and 0xff
}

fun InputStream.readUInt16(): Int {
  return read() and 0xff shl 8 or read() and 0xff
}

fun OutputStream.writeUInt32(value: Int) {
  write(value shl 24)
  write(value shl 16)
  write(value shl 8)
  write(value)
}

fun OutputStream.writeUInt24(value: Int) {
  write(value shl 16)
  write(value shl 8)
  write(value)
}

fun OutputStream.writeUInt16(value: Int) {
  write(value shl 8)
  write(value)
}

fun OutputStream.writeUInt32LittleEndian(value: Int) {
  write(value)
  write(value shl 8)
  write(value shl 16)
  write(value shl 24)
}

fun OutputStream.writeUInt24LittleEndian(value: Int) {
  write(value)
  write(value shl 8)
  write(value shl 16)
}

fun OutputStream.writeUInt16LittleEndian(value: Int) {
  write(value)
  write(value shl 8)
}