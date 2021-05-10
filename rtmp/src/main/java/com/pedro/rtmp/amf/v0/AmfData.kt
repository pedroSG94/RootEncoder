package com.pedro.rtmp.amf.v0

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/04/21.
 */
abstract class AmfData {

  companion object {

    /**
     * Read unknown AmfData and convert it to specific class
     */
    @Throws(IOException::class)
    fun getAmfData(input: InputStream): AmfData {
      val amfData = when (val type = getMarkType(input.read())) {
        AmfType.NUMBER -> AmfNumber()
        AmfType.BOOLEAN -> AmfBoolean()
        AmfType.STRING -> AmfString()
        AmfType.OBJECT -> AmfObject()
        AmfType.NULL -> AmfNull()
        AmfType.UNDEFINED -> AmfUndefined()
        AmfType.ECMA_ARRAY -> AmfEcmaArray()
        AmfType.STRICT_ARRAY -> AmfStrictArray()
        else -> throw IOException("Unimplemented AMF data type: ${type.name}")
      }
      amfData.readBody(input)
      return amfData
    }

    fun getMarkType(type: Int): AmfType {
      return AmfType.values().find { it.mark.toInt() == type } ?: AmfType.STRING
    }
  }

  @Throws(IOException::class)
  fun readHeader(input: InputStream): AmfType {
    return getMarkType(input.read())
  }

  @Throws(IOException::class)
  fun writeHeader(output: OutputStream) {
    output.write(getType().mark.toInt())
  }

  @Throws(IOException::class)
  abstract fun readBody(input: InputStream)

  @Throws(IOException::class)
  abstract fun writeBody(output: OutputStream)

  abstract fun getType(): AmfType

  //Body size without header type
  abstract fun getSize(): Int
}