package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/04/21.
 *
 * Exactly the same that AmfObject but start with an UInt32 that indicate the number of lines in Map
 */
class AmfEcmaArray(private val properties: HashMap<AmfString, AmfData> = HashMap()): AmfObject(properties) {

  var length = 0

  init {
    // add length size to body
    bodySize += 4
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    bodySize = 0
    //get number of items as UInt32
    length = input.readUInt32()
    bodySize += 4
    //read items
    super.readBody(input)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    //write number of items in the list as UInt32
    output.writeUInt32(properties.size)
    //write items
    super.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.ECMA_ARRAY

  override fun toString(): String {
    return "AmfEcmaArray length: $length, properties: $properties"
  }
}