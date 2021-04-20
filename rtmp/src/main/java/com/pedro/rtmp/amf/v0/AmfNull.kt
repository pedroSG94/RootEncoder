package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class AmfNull: AmfData() {

  override fun readBody(input: InputStream) {
    //no body to read
  }

  override fun writeBody(output: OutputStream) {
    //no body to write
  }

  override fun getType(): AmfType = AmfType.NULL

  override fun getSize(): Int = 0
}