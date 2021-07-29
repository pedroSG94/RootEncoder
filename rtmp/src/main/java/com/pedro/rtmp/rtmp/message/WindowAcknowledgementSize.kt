package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class WindowAcknowledgementSize(var acknowledgementWindowSize: Int = 0, timeStamp: Int = 0):
    RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL.mark)) {

  init {
    header.timeStamp = timeStamp
  }

  override fun readBody(input: InputStream) {
    acknowledgementWindowSize = input.readUInt32()
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.writeUInt32(acknowledgementWindowSize)
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE

  override fun getSize(): Int = 4

  override fun toString(): String {
    return "WindowAcknowledgementSize(acknowledgementWindowSize=$acknowledgementWindowSize)"
  }
}