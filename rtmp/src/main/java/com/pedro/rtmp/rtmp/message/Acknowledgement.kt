package com.pedro.rtmp.rtmp.message

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
class Acknowledgement: RtmpMessage() {
  override fun readBody(input: InputStream) {
    TODO("Not yet implemented")
  }

  override fun storeBody(): ByteArray {
    TODO("Not yet implemented")
  }

  override fun getType(): MessageType {
    TODO("Not yet implemented")
  }

  override fun getSize(): Int {
    TODO("Not yet implemented")
  }
  override fun updateHeader(): RtmpHeader {
    TODO("Not yet implemented")
  }
}