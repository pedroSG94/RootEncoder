package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.utils.readUntil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 20/04/21.
 *
 * Packet used to indicate end of AmfObject and AmfEcmaArray.
 * This is a final sequence of 3 bytes.
 */
class AmfObjectEnd(var found: Boolean = false): AmfData() {

  private val endSequence = byteArrayOf(0x00, 0x00, getType().mark)

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize())
    input.readUntil(bytes)
    found = bytes contentEquals endSequence
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.write(endSequence)
  }

  override fun getType(): AmfType = AmfType.OBJECT_END

  override fun getSize(): Int = endSequence.size

  override fun toString(): String {
    return "AmfObjectEnd"
  }
}