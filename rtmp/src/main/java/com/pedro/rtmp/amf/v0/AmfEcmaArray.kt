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

import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 *
 * Exactly the same that AmfObject but start with an UInt32 that indicate the number of lines in Map
 */
class AmfEcmaArray(private val properties: LinkedHashMap<AmfString, AmfData> = LinkedHashMap()): AmfObject(properties) {

  var length = 0

  init {
    // add length size to body
    bodySize += 4
  }

  override fun setProperty(name: String, data: String) {
    super.setProperty(name, data)
    length = properties.size
  }

  override fun setProperty(name: String, data: Boolean) {
    super.setProperty(name, data)
    length = properties.size
  }

  override fun setProperty(name: String) {
    super.setProperty(name)
    length = properties.size
  }

  override fun setProperty(name: String, data: Double) {
    super.setProperty(name, data)
    length = properties.size
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    //get number of items as UInt32
    length = input.readUInt32()
    //read items
    super.readBody(input)
    bodySize += 4 //add length size to body
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    //write number of items in the list as UInt32
    output.writeUInt32(length)
    //write items
    super.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.ECMA_ARRAY

  override fun toString(): String {
    return "AmfEcmaArray length: $length, properties: $properties"
  }
}