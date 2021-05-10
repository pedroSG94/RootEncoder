package com.pedro.rtmp.amf.v0

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/04/21.
 *
 * Contain an empty body
 */
class AmfUndefined: AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    //no body to read
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    //no body to write
  }

  override fun getType(): AmfType = AmfType.UNDEFINED

  override fun getSize(): Int = 0

  override fun toString(): String {
    return "AmfUndefined"
  }
}