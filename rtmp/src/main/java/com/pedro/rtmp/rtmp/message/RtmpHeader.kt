package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.readUInt24
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt24
import com.pedro.rtmp.utils.writeUInt32
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 */
class RtmpHeader(var basicHeader: BasicHeader? = null) {

  var timeStamp = 0
  var messageLength = 0
  var messageType: MessageType? = null
  var messageStreamId = 0

  @Throws(IOException::class)
  fun readHeader(input: InputStream) {
    val basicHeader = BasicHeader.parseBasicHeader(input.read().toByte())
    when (basicHeader.chunkType) {
      ChunkType.TYPE_0 -> {
        timeStamp = input.readUInt24()
        messageLength = input.readUInt24()
        messageType = RtmpMessage.getMarkType(input.read())
        messageStreamId = input.readUInt32()
      }
      ChunkType.TYPE_1 -> {
        timeStamp = input.readUInt24()
        messageLength = input.readUInt24()
        messageType = RtmpMessage.getMarkType(input.read())
      }
      ChunkType.TYPE_2 -> {
        timeStamp = input.readUInt24()
      }
      ChunkType.TYPE_3 -> {
        //No header to read
      }
    }
  }

  @Throws(IOException::class)
  fun writeHeader(output: OutputStream) {
    basicHeader?.let {
      writeHeader(it, output)
    }
  }

  private fun writeHeader(basicHeader: BasicHeader, output: OutputStream) {
    output.write((basicHeader.chunkType.mark.toInt() shl 6) or basicHeader.chunkStreamId)
    when (basicHeader.chunkType) {
      ChunkType.TYPE_0 -> {
        output.writeUInt24(timeStamp)
        output.writeUInt24(messageLength)
        messageType?.let { messageType ->
          output.write(messageType.mark.toInt())
        }
        output.writeUInt32(messageStreamId)
      }
      ChunkType.TYPE_1 -> {
        output.writeUInt24(timeStamp)
        output.writeUInt24(messageLength)
        messageType?.let { messageType ->
          output.write(messageType.mark.toInt())
        }
      }
      ChunkType.TYPE_2 -> {
        output.writeUInt24(timeStamp)
      }
      ChunkType.TYPE_3 -> {
        //No header to write
      }
    }
  }

  fun getPacketLength(): Int = messageLength
}