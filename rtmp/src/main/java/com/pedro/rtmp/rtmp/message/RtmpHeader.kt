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
import com.pedro.rtmp.utils.socket.RtmpSocket
import java.io.IOException
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
    suspend fun readHeader(socket: RtmpSocket, commandSessionHistory: CommandSessionHistory,
      timestamp: Int = 0): RtmpHeader {
      val basicHeader = BasicHeader.parseBasicHeader(socket)
      var timeStamp = timestamp
      var messageLength = 0
      var messageType: MessageType? = null
      var messageStreamId = 0
      val lastHeader = commandSessionHistory.getLastReadHeader(basicHeader.chunkStreamId)
      when (basicHeader.chunkType) {
        ChunkType.TYPE_0 -> {
          timeStamp = socket.readUInt24()
          messageLength = socket.readUInt24()
          messageType = RtmpMessage.getMarkType(socket.read())
          messageStreamId = socket.readUInt32LittleEndian()
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = socket.readUInt32()
          }
        }
        ChunkType.TYPE_1 -> {
          if (lastHeader != null) {
            messageStreamId = lastHeader.messageStreamId
          }
          timeStamp = socket.readUInt24()
          messageLength = socket.readUInt24()
          messageType = RtmpMessage.getMarkType(socket.read())
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = socket.readUInt32()
          }
        }
        ChunkType.TYPE_2 -> {
          if (lastHeader != null) {
            messageLength = lastHeader.messageLength
            messageType = lastHeader.messageType
            messageStreamId = lastHeader.messageStreamId
          }
          timeStamp = socket.readUInt24()
          //extended timestamp
          if (timeStamp >= 0xffffff) {
            timeStamp = socket.readUInt32()
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
            timeStamp = socket.readUInt32()
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
  suspend fun writeHeader(socket: RtmpSocket) {
    writeHeader(basicHeader, socket)
  }

  /**
   * Check ChunkType class to know header structure
   */
  suspend fun writeHeader(basicHeader: BasicHeader, socket: RtmpSocket) {
    // Write basic header byte
    socket.write((basicHeader.chunkType.mark.toInt() shl 6) or basicHeader.chunkStreamId)
    when (basicHeader.chunkType) {
      ChunkType.TYPE_0 -> {
        socket.writeUInt24(min(timeStamp, 0xffffff))
        socket.writeUInt24(messageLength)
        messageType?.let { messageType ->
          socket.write(messageType.mark.toInt())
        }
        socket.writeUInt32LittleEndian(messageStreamId)
        //extended timestamp
        if (timeStamp > 0xffffff) {
          socket.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_1 -> {
        socket.writeUInt24(min(timeStamp, 0xffffff))
        socket.writeUInt24(messageLength)
        messageType?.let { messageType ->
          socket.write(messageType.mark.toInt())
        }
        //extended timestamp
        if (timeStamp > 0xffffff) {
          socket.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_2 -> {
        socket.writeUInt24(min(timeStamp, 0xffffff))
        //extended timestamp
        if (timeStamp > 0xffffff) {
          socket.writeUInt32(timeStamp)
        }
      }
      ChunkType.TYPE_3 -> {
        //extended timestamp
        if (timeStamp > 0xffffff) {
          socket.writeUInt32(timeStamp)
        }
      }
    }
  }

  fun getPacketLength(): Int = messageLength + basicHeader.getHeaderSize(timeStamp)

  override fun toString(): String {
    return "RtmpHeader(timeStamp=$timeStamp, messageLength=$messageLength, messageType=$messageType, messageStreamId=$messageStreamId, basicHeader=$basicHeader)"
  }
}