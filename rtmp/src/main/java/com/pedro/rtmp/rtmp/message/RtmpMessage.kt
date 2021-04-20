package com.pedro.rtmp.rtmp.message

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
abstract class RtmpMessage {

  protected val header = RtmpHeader()

  fun readHeader(input: InputStream) {
    header.readHeader(input)
  }

  fun writeHeader(output: OutputStream) {
    header.writeHeader(output)
  }

  abstract fun readBody(input: InputStream)
  abstract fun writeBody(output: OutputStream)
  abstract fun getSize(): Int
}