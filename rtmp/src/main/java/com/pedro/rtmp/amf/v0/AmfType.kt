package com.pedro.rtmp.amf.v0

/**
 * Created by pedro on 20/04/21.
 */
enum class AmfType(val mark: Byte) {
  NUMBER(0x00), BOOLEAN(0x01), STRING(0x02), OBJECT(0x03),
  NULL(0x05), UNDEFINED(0x06), ECMA_ARRAY(0x08), STRICT_ARRAY(0x0A),

  /**
   * Not used in RTMP
   */
  REFERENCE(0x07), DATE(0x0B), LONG_STRING(0x0C), OBJECT_END(0x09),
  UNSUPPORTED(0x0D), XML_DOCUMENT(0x0F), TYPED_OBJECT(0x10),

  /**
   * reserved, not supported
   */
  MOVIE_CLIP(0x04), RECORD_SET(0x0E),
}