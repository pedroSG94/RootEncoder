package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/04/21.
 *
 * Only 1 bit of size where 0 is false and another value is true
 */
class AmfBoolean(private var value: Boolean = false): AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val b = input.read()
    this.value = b != 0
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.write(if (value) 1 else 0)
  }

  override fun getType(): AmfType = AmfType.BOOLEAN

  override fun getSize(): Int = 1
}