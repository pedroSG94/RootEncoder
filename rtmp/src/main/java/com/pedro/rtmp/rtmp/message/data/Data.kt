package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.rtmp.message.RtmpMessage
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
abstract class Data: RtmpMessage() {
  override fun readBody(input: InputStream) {
    TODO("Not yet implemented")
  }

  override fun writeBody(output: OutputStream) {
    TODO("Not yet implemented")
  }

  override fun getSize(): Int {
    TODO("Not yet implemented")
  }
}