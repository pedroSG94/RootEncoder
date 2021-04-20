package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class AmfBoolean(private var value: Boolean = false): AmfData() {

  override fun readBody(input: InputStream) {
    val b = input.read()
    this.value = b != 0
  }

  override fun writeBody(output: OutputStream) {
    output.write(if (value) 1 else 0)
  }

  override fun getType(): AmfType = AmfType.BOOLEAN

  override fun getSize(): Int = 1
}