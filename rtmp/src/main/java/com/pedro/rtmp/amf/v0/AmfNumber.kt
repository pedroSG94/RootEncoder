package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/04/21.
 */
class AmfNumber(private var value: Double = 0.0): AmfData() {

  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize())
    input.read(bytes)
    val value = ByteBuffer.wrap(bytes).long
    this.value = Double.Companion.fromBits(value)
  }

  override fun writeBody(output: OutputStream) {
    val byteBuffer = ByteBuffer.allocate(getSize()).putLong(value.toRawBits())
    output.write(byteBuffer.array())
  }

  override fun getType(): AmfType = AmfType.NUMBER

  override fun getSize(): Int = 8
}