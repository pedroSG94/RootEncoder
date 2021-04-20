package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
open class AmfObject(private val properties: HashMap<AmfString, AmfData> = HashMap()): AmfData() {

  protected var bodySize = 0

  init {
    properties.forEach {
      //Get size of all elements and include in size value. + 1 because include header size
      bodySize += it.key.getSize() + 1
      bodySize += it.value.getSize() + 1
    }
    val objectEnd = AmfObjectEnd()
    bodySize += objectEnd.getSize()
  }

  override fun readBody(input: InputStream) {
    val objectEnd = AmfObjectEnd()
    val markInputStream: InputStream = if (input.markSupported()) input else BufferedInputStream(input)
    while (!objectEnd.found) {
      markInputStream.mark(objectEnd.getSize())
      objectEnd.readBody(input)
      if (objectEnd.found) {
        bodySize += objectEnd.getSize()
      } else {
        markInputStream.reset()

        val key = AmfString()
        key.readHeader(input)
        key.readBody(input)
        bodySize += key.getSize() + 1

        val value = getAmfData(input)
        bodySize += value.getSize() + 1

        properties[key] = value
      }
    }
  }

  override fun writeBody(output: OutputStream) {
    properties.forEach {
      it.key.writeHeader(output)
      it.key.writeBody(output)

      it.value.writeHeader(output)
      it.value.writeBody(output)
    }
    val objectEnd = AmfObjectEnd()
    objectEnd.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.OBJECT

  override fun getSize(): Int = bodySize
}