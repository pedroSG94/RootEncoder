package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class SetPeerBandwidth(private var acknowledgementWindowSize: Int = 0, private var type: Type = Type.DYNAMIC):
    RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL.mark)) {

  enum class Type(val mark: Byte) {
    HARD(0x00), SOFT(0x01), DYNAMIC(0x02)
  }

  override fun readBody(input: InputStream) {
    acknowledgementWindowSize = input.readUInt32()
    val t = input.read().toByte()
    type = Type.values().find { it.mark == t } ?: throw IOException("Unknown bandwidth type: $t")
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.writeUInt32(acknowledgementWindowSize)
    byteArrayOutputStream.write(type.mark.toInt())
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.SET_PEER_BANDWIDTH

  override fun getSize(): Int = 9

  override fun toString(): String {
    return "SetPeerBandwidth(acknowledgementWindowSize=$acknowledgementWindowSize, type=$type)"
  }
}