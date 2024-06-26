/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Created by pedro on 20/04/21.
 */
class RtmpHeader(var basicHeader: BasicHeader) {

  var timeStamp: Int = 0
  var messageLength: Int = 0
  var messageType: MessageType? = null
  var messageStreamId: Int = 0

  companion object {

    private const val TAG = "RtmpHeader"

    /**
     * Check ChunkType class to know header structure
     */
    @Throws(IOException::class)
    fun readHeader(input: InputStream, commandSessionHistory: CommandSessionHistory,
      timestamp: Int = 0): RtmpHeader {
      val basicHeader = BasicHeader.parseBasicHeader(input)
      var timeStamp = timestamp
      var messageLength = 0
      var messageType: MessageType? = null
      var messageStreamId = 0
      val lastHeader = commandSessionHistory.getLastReadHeader(basicHeader.chunkStreamId)
      when (basicHeader.chunkType) {
        ChunkType.TYPE_0 -> {
          timeStamp = input.readUInt24()
          messageLength = input.readUInt24()
          messageType = RtmpMessage.getMarkType(input.read())
          messageStreamId = input.readUInt32LittleEndian()
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = input.readUInt32()
          }
        }
        ChunkType.TYPE_1 -> {
          if (lastHeader != null) {
            messageStreamId = lastHeader.messageStreamId
          }
          timeStamp = input.readUInt24()
          messageLength = input.readUInt24()
          messageType = RtmpMessage.getMarkType(input.read())
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = input.readUInt32()
          }
        }
        ChunkType.TYPE_2 -> {
          if (lastHeader != null) {
            messageLength = lastHeader.messageLength
            messageType = lastHeader.messageType
            messageStreamId = lastHeader.messageStreamId
          }
          timeStamp = input.readUInt24()
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = input.readUInt32()
          }
        }
        ChunkType.TYPE_3 -> {
          if (lastHeader != null) {
            timeStamp = lastHeader.timeStamp
            messageLength = lastHeader.messageLength
            messageType = lastHeader.messageType
            messageStreamId = lastHeader.messageStreamId
          }
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = input.readUInt32()
          }
          //No header to read
        }
      }
      val rtmpHeader = RtmpHeader(basicHeader)
      rtmpHeader.timeStamp = timeStamp
      rtmpHeader.messageLength = messageLength
      rtmpHeader.messageType = messageType
      rtmpHeader.messageStreamId = messageStreamId
      return rtmpHeader
    }
  }

  @Throws(IOException::class)
  fun writeHeader(output: OutputStream) {
    writeHeader(basicHeader, output)
  }

  /**
   * Check ChunkType class to know header structure
   */
  @Throws(IOException::class)
  fun writeHeader(basicHeader: BasicHeader, output: OutputStream) {
    // Write basic header byte
    output.write((basicHeader.chunkType.mark.toInt() shl 6) or basicHeader.chunkStreamId)
    when (basicHeader.chunkType) {
      ChunkType.TYPE_0 -> {
        output.writeUInt24(min(timeStamp, 0xffffff))
        output.writeUInt24(messageLength)
        messageType?.let { messageType ->
          output.write(messageType.mark.toInt())
        }
        output.writeUInt32LittleEndian(messageStreamId)
        //extended timestamp
        if (timeStamp > 0xffffff) {
          output.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_1 -> {
        output.writeUInt24(min(timeStamp, 0xffffff))
        output.writeUInt24(messageLength)
        messageType?.let { messageType ->
          output.write(messageType.mark.toInt())
        }
        //extended timestamp
        if (timeStamp > 0xffffff) {
          output.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_2 -> {
        output.writeUInt24(min(timeStamp, 0xffffff))
        //extended timestamp
        if (timeStamp > 0xffffff) {
          output.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_3 -> {
        //extended timestamp
        if (timeStamp > 0xffffff) {
          output.writeUInt32(timeStamp)
        }
      }
    }
  }

  fun getPacketLength(): Int = messageLength + basicHeader.getHeaderSize(timeStamp)

  override fun toString(): String {
    return "RtmpHeader(timeStamp=$timeStamp, messageLength=$messageLength, messageType=$messageType, messageStreamId=$messageStreamId, basicHeader=$basicHeader)"
  }
}