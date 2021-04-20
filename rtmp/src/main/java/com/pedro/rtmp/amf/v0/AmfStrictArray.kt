package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class AmfStrictArray(val items: MutableList<AmfData> = mutableListOf()): AmfData() {

  private var bodySize = 0

  init {
    items.forEach {
      bodySize += it.getSize() + 1
    }
  }

  override fun readBody(input: InputStream) {
    //get number of items as UInt32
    val length = input.read() and 0xff shl 24 or (input.read() and 0xff shl 16) or
        (input.read() and 0xff shl 8) or (input.read() and 0xff)
    bodySize += 4
    //read items
    for (i in 0 until length) {
      val amfData = AmfData.getAmfData(input)
      bodySize += amfData.getSize() + 1
      items.add(amfData)
    }
  }

  override fun writeBody(output: OutputStream) {
    //write number of items in the list as UInt32
    output.write(items.size ushr 24)
    output.write(items.size ushr 16)
    output.write(items.size ushr 8)
    output.write(items.size)
    //write items
    items.forEach {
      it.writeHeader(output)
      it.writeBody(output)
    }
  }

  override fun getType(): AmfType = AmfType.STRICT_ARRAY

  override fun getSize(): Int = bodySize
}