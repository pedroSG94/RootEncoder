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