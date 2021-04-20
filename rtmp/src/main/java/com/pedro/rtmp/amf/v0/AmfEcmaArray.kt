package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class AmfEcmaArray(val properties: HashMap<AmfString, AmfData> = HashMap()): AmfObject(properties) {

  var length = 0

  init {
    // add length size to body
    bodySize += 4
  }

  override fun readBody(input: InputStream) {
    //get number of items as UInt32
    length = input.read() and 0xff shl 24 or (input.read() and 0xff shl 16) or
        (input.read() and 0xff shl 8) or (input.read() and 0xff)
    bodySize += 4
    //read items
    super.readBody(input)
  }

  override fun writeBody(output: OutputStream) {
    //write number of items in the list as UInt32
    output.write(properties.size ushr 24)
    output.write(properties.size ushr 16)
    output.write(properties.size ushr 8)
    output.write(properties.size)
    //write items
    super.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.ECMA_ARRAY
}