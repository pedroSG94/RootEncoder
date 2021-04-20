package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 8/04/21.
 */
class AmfString(private var value: String = ""): AmfData() {

  private var bodySize: Int = value.length

  override fun readBody(input: InputStream) {
    //read value size as UInt16
    bodySize = input.read() and 0xff shl 8 or (input.read() and 0xff)
    //read value in ASCII
    val bytes = ByteArray(bodySize)
    input.read(bytes)
    value = String(bytes, Charsets.US_ASCII)
  }

  override fun writeBody(output: OutputStream) {
    val bytes = value.toByteArray(Charsets.US_ASCII)
    //write value size as UInt16
    output.write(bodySize ushr 8)
    output.write(bodySize)
    //write value bytes in ASCII
    output.write(bytes)
  }

  override fun getType(): AmfType = AmfType.STRING

  override fun getSize(): Int = bodySize
}