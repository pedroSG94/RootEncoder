package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.utils.readUInt16
import com.pedro.rtmp.utils.writeUInt16
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 8/04/21.
 */
class AmfString(var value: String = ""): AmfData() {

  private var bodySize: Int = value.length

  override fun readBody(input: InputStream) {
    //read value size as UInt16
    bodySize = input.readUInt16()
    //read value in ASCII
    val bytes = ByteArray(bodySize)
    input.read(bytes)
    value = String(bytes, Charsets.US_ASCII)
  }

  override fun writeBody(output: OutputStream) {
    val bytes = value.toByteArray(Charsets.US_ASCII)
    //write value size as UInt16
    output.writeUInt16(bodySize)
    //write value bytes in ASCII
    output.write(bytes)
  }

  override fun getType(): AmfType = AmfType.STRING

  override fun getSize(): Int = bodySize
}