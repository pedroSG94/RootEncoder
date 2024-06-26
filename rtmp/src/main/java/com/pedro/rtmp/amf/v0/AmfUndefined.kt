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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 *
 * Contain an empty body
 */
class AmfUndefined: AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    //no body to read
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    //no body to write
  }

  override fun getType(): AmfType = AmfType.UNDEFINED

  override fun getSize(): Int = 0

  override fun toString(): String {
    return "AmfUndefined"
  }
}