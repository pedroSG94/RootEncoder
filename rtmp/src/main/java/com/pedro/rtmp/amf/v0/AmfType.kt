/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.amf.v0

/**
 * Created by pedro on 20/04/21.
 */
enum class AmfType(val mark: Byte) {
  NUMBER(0x00), BOOLEAN(0x01), STRING(0x02), OBJECT(0x03),
  NULL(0x05), UNDEFINED(0x06), ECMA_ARRAY(0x08), OBJECT_END(0x09),
  STRICT_ARRAY(0x0A), DATE(0x0B), LONG_STRING(0x0C), UNSUPPORTED(0x0D),
  XML_DOCUMENT(0x0F),
  /**
   * Not implemented
   */
  REFERENCE(0x07), TYPED_OBJECT(0x10), AVM_PLUS_OBJECT(0x11),

  /**
   * reserved, not supported
   */
  MOVIE_CLIP(0x04), RECORD_SET(0x0E),
}