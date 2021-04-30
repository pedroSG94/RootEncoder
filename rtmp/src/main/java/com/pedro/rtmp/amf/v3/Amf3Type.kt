package com.pedro.rtmp.amf.v3

/**
 * Created by pedro on 29/04/21.
 */
enum class Amf3Type(val mark: Byte) {
  UNDEFINED(0x00), NULL(0x01), TRUE(0x02), FALSE(0x03), INTEGER(0x04), 
  DOUBLE(0x05), STRING(0x06), XML_DOC(0x07), DATE(0x08), ARRAY(0x09), 
  OBJECT(0x0A), XML(0x0B), BYTE_ARRAY(0x0C), VECTOR_INT(0x0D), VECTOR_UINT(0x0E), 
  VECTOR_DOUBLE(0x0F), VECTOR_OBJECT(0x10), DICTIONARY(0x11)
}