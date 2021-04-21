package com.pedro.rtmp.rtmp.message.control

import android.util.Log
import com.pedro.rtmp.rtmp.message.MessageType
import com.pedro.rtmp.rtmp.message.RtmpHeader
import com.pedro.rtmp.rtmp.message.RtmpMessage
import com.pedro.rtmp.utils.readUInt16
import com.pedro.rtmp.utils.readUInt32
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
class UserControl: RtmpMessage() {

  private val TAG = "UserControl"
  private var bodySize = 0

  override fun updateHeader(): RtmpHeader {
    TODO("Not yet implemented")
  }

  override fun readBody(input: InputStream) {
    bodySize = 0
    val type = input.readUInt16()
    bodySize += 2
    val data = input.readUInt32()
    val event: Event
    if (type == 3) {
      val bufferLength = input.readUInt32()
      event = Event(data, bufferLength)
    } else {
      event = Event(data)
    }
    Log.i(TAG, "type: $type, event: $event")
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
}