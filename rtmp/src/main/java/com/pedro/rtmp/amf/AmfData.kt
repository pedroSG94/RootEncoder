package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
abstract class AmfData {

  companion object {

    /**
     * Read unknown AmfData and convert it to specific class
     */
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
      return when (type) {
        AmfType.NUMBER.mark.toInt() -> AmfType.NUMBER
        AmfType.BOOLEAN.mark.toInt() -> AmfType.BOOLEAN
        AmfType.STRING.mark.toInt() -> AmfType.STRING
        AmfType.OBJECT.mark.toInt() -> AmfType.OBJECT
        AmfType.NULL.mark.toInt() -> AmfType.NULL
        AmfType.UNDEFINED.mark.toInt() -> AmfType.UNDEFINED
        AmfType.ECMA_ARRAY.mark.toInt() -> AmfType.ECMA_ARRAY
        AmfType.STRICT_ARRAY.mark.toInt() -> AmfType.STRICT_ARRAY
        //Unused
        AmfType.REFERENCE.mark.toInt() -> AmfType.REFERENCE
        AmfType.DATE.mark.toInt() -> AmfType.DATE
        AmfType.LONG_STRING.mark.toInt() -> AmfType.LONG_STRING
        AmfType.OBJECT_END.mark.toInt() -> AmfType.OBJECT_END
        AmfType.UNSUPPORTED.mark.toInt() -> AmfType.UNSUPPORTED
        AmfType.XML_DOCUMENT.mark.toInt() -> AmfType.XML_DOCUMENT
        AmfType.TYPED_OBJECT.mark.toInt() -> AmfType.TYPED_OBJECT
        //Reserved
        AmfType.MOVIE_CLIP.mark.toInt() -> AmfType.MOVIE_CLIP
        AmfType.RECORD_SET.mark.toInt() -> AmfType.RECORD_SET
        else -> {
          throw IOException("Unknown AMF data type: $type")
        }
      }
    }
  }

  fun readHeader(input: InputStream): AmfType {
    return getMarkType(input.read())
  }

  fun writeHeader(output: OutputStream) {
    output.write(getType().mark.toInt())
  }

  abstract fun readBody(input: InputStream)
  abstract fun writeBody(output: OutputStream)
  abstract fun getType(): AmfType
  //Body size without header type
  abstract fun getSize(): Int
}