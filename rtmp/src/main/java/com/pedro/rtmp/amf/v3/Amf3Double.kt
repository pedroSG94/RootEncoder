package com.pedro.rtmp.amf.v3

import com.pedro.rtmp.utils.readUntil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.jvm.Throws

/**
 * Created by pedro on 29/04/21.
 */
class Amf3Double(var value: Double = 0.0): Amf3Data() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize())
    input.readUntil(bytes)
    val value = ByteBuffer.wrap(bytes).long
    this.value = Double.Companion.fromBits(value)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    val byteBuffer = ByteBuffer.allocate(getSize()).putLong(value.toRawBits())
    output.write(byteBuffer.array())
  }

  override fun getType(): Amf3Type = Amf3Type.DOUBLE

  override fun getSize(): Int = 8
}