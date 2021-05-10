package com.pedro.rtmp.amf.v3

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 */
class Amf3False: Amf3Data() {

  override fun readBody(input: InputStream) {
    //no body to read
  }

  override fun writeBody(output: OutputStream) {
    //no body to write
  }

  override fun getType(): Amf3Type = Amf3Type.FALSE

  override fun getSize(): Int = 0
}