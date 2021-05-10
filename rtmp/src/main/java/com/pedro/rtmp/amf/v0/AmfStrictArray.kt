package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

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