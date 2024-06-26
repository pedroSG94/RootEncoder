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
 * A list of any amf packets that start with an UInt32 to indicate number of items
 */
class AmfStrictArray(val items: MutableList<AmfData> = mutableListOf()): AmfData() {

  private var bodySize = 0

  init {
    bodySize += 4
    items.forEach {
      bodySize += it.getSize() + 1
    }
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    items.clear()
    bodySize = 0
    //get number of items as UInt32
    val length = input.readUInt32()
    bodySize += 4
    //read items
    for (i in 0 until length) {
      val amfData = getAmfData(input)
      bodySize += amfData.getSize() + 1
      items.add(amfData)
    }
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    //write number of items in the list as UInt32
    output.writeUInt32(items.size)
    //write items
    items.forEach {
      it.writeHeader(output)
      it.writeBody(output)
    }
  }

  override fun getType(): AmfType = AmfType.STRICT_ARRAY

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "AmfStrictArray items: ${items.toTypedArray().contentToString()}"
  }
}