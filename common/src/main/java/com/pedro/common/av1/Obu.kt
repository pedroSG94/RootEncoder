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

package com.pedro.common.av1

/**
 * Created by pedro on 8/12/23.
 */
data class Obu(
  val header: ByteArray, //header and optional extension header
  val leb128: ByteArray?, //this is the length value encoded in leb128 mode
  val data: ByteArray
) {
  fun getFullData(): ByteArray {
    return header.plus(leb128 ?: byteArrayOf()).plus(data)
  }
}
