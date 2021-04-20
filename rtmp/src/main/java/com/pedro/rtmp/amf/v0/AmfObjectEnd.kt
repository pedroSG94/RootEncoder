package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class AmfObjectEnd(var found: Boolean = false): AmfData() {

  private val endSequence = byteArrayOf(0x00, 0x00, 0x09)

  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize())
    input.read(bytes)
    found = bytes contentEquals endSequence
  }

  override fun writeBody(output: OutputStream) {
    output.write(endSequence)
  }

  override fun getType(): AmfType = AmfType.OBJECT_END

  override fun getSize(): Int = endSequence.size

}