package com.pedro.rtmp.amf.v0

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.LinkedHashMap

/**
 * Created by pedro on 20/04/21.
 *
 * A Map of others amf packets where key is an AmfString and value could be any amf packet
 */
open class AmfObject(private val properties: HashMap<AmfString, AmfData> = LinkedHashMap()): AmfData() {

  protected var bodySize = 0

  init {
    properties.forEach {
      //Get size of all elements and include in size value. + 1 because include header size
      bodySize += it.key.getSize()
      bodySize += it.value.getSize() + 1
    }
    val objectEnd = AmfObjectEnd()
    bodySize += objectEnd.getSize()
  }

  fun getProperty(name: String): AmfData? {
    properties.forEach {
      if (it.key.value == name) {
        return it.value
      }
    }
    return null
  }

  open fun setProperty(name: String, data: String) {
    val key = AmfString(name)
    val value = AmfString(data)
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String, data: Boolean) {
    val key = AmfString(name)
    val value = AmfBoolean(data)
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String) {
    val key = AmfString(name)
    val value = AmfNull()
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String, data: Double) {
    val key = AmfString(name)
    val value = AmfNumber(data)
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    properties.clear()
    bodySize = 0
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
        key.readBody(input)
        bodySize += key.getSize()

        val value = getAmfData(input)
        bodySize += value.getSize() + 1

        properties[key] = value
      }
    }
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    properties.forEach {
      it.key.writeBody(output)

      it.value.writeHeader(output)
      it.value.writeBody(output)
    }
    val objectEnd = AmfObjectEnd()
    objectEnd.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.OBJECT

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "AmfObject properties: $properties"
  }
}