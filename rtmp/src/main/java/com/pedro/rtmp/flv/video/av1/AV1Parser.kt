/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.rtmp.flv.video.av1

/**
 * Created by pedro on 8/12/23.
 *
 * AV1 packets contains a sequence of OBUs.
 * Each OBU contain:
 * - header -> 1 to 2 bytes
 *
 * obu_forbidden_bit f(1)
 * obu_type f(4)
 * obu_extension_flag f(1)
 * obu_has_size_field f(1)
 * obu_reserved_1bit f(1)
 * if (obu_extension_flag == 1 )
 *   obu_extension_header()
 * }
 *
 * extension header:
 *
 * temporal_id f(3)
 * spatial_id f(2)
 * extension_header_reserved_3bit f(3)
 *
 * - data length (optional depend of header) -> 1 to 8 bytes in leb128
 * - data
 */
class AV1Parser {

  fun getObuType(header: Byte): ObuType {
    val value = header.toInt() and 0b10000111
    return ObuType.values().firstOrNull { it.value == value } ?: ObuType.RESERVED
  }

  fun getObus(av1Data: ByteArray): List<Obu> {

    return listOf()
  }

  fun writeLeb128(length: Long): ByteArray {
    return byteArrayOf()
  }

  fun readLeb128(data: ByteArray): Long {
    return 0
  }
}