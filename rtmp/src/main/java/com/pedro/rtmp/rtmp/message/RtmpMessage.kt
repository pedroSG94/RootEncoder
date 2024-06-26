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
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.command.CommandAmf3
import com.pedro.rtmp.rtmp.message.control.UserControl
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import com.pedro.rtmp.rtmp.message.data.DataAmf3
import com.pedro.rtmp.rtmp.message.shared.SharedObjectAmf0
import com.pedro.rtmp.rtmp.message.shared.SharedObjectAmf3
import com.pedro.rtmp.utils.CommandSessionHistory
import com.pedro.rtmp.utils.RtmpConfig
import com.pedro.rtmp.utils.readUntil
import java.io.*

/**
 * Created by pedro on 20/04/21.
 */
abstract class RtmpMessage(basicHeader: BasicHeader) {

  val header by lazy {
    RtmpHeader(basicHeader).apply {
      messageType = getType()
      messageLength = getSize()
    }
  }

  companion object {

    private const val TAG = "RtmpMessage"

    @Throws(IOException::class)
    fun getRtmpMessage(input: InputStream, chunkSize: Int,
      commandSessionHistory: CommandSessionHistory): RtmpMessage {
      val header = RtmpHeader.readHeader(input, commandSessionHistory)
      val rtmpMessage = when (header.messageType) {
        MessageType.SET_CHUNK_SIZE -> SetChunkSize()
        MessageType.ABORT -> Abort()
        MessageType.ACKNOWLEDGEMENT -> Acknowledgement()
        MessageType.USER_CONTROL -> UserControl()
        MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> WindowAcknowledgementSize()
        MessageType.SET_PEER_BANDWIDTH -> SetPeerBandwidth()
        MessageType.AUDIO -> Audio()
        MessageType.VIDEO -> Video()
        MessageType.DATA_AMF3 -> DataAmf3()
        MessageType.SHARED_OBJECT_AMF3 -> SharedObjectAmf3()
        MessageType.COMMAND_AMF3 -> CommandAmf3()
        MessageType.DATA_AMF0 -> DataAmf0()
        MessageType.SHARED_OBJECT_AMF0 -> SharedObjectAmf0()
        MessageType.COMMAND_AMF0 -> CommandAmf0()
        MessageType.AGGREGATE -> Aggregate()
        else -> throw IOException("Unimplemented message type: ${header.messageType}")
      }
      rtmpMessage.updateHeader(header)
      //we have multiple chunk wait until we have full body on stream and discard chunk header
      val bodyInput = if (header.messageLength > chunkSize) {
        getInputWithoutChunks(input, header, chunkSize, commandSessionHistory)
      } else {
        input
      }
      rtmpMessage.readBody(bodyInput)
      return rtmpMessage
    }

    fun getMarkType(type: Int): MessageType {
      return MessageType.entries.find { it.mark.toInt() == type } ?: throw IOException("Unknown rtmp message type: $type")
    }

    private fun getInputWithoutChunks(input: InputStream, header: RtmpHeader, chunkSize: Int,
      commandSessionHistory: CommandSessionHistory): InputStream {
      val packetStore = ByteArrayOutputStream()
      var bytesRead = 0
      while (bytesRead < header.messageLength) {
        var chunk: ByteArray
        if (header.messageLength - bytesRead < chunkSize) {
          //last chunk
          chunk = ByteArray(header.messageLength - bytesRead)
          input.readUntil(chunk)
        } else {
          chunk = ByteArray(chunkSize)
          input.readUntil(chunk)
          //skip chunk header to discard it, set packet ts to indicate if you need read extended ts
          RtmpHeader.readHeader(input, commandSessionHistory, header.timeStamp)
        }
        bytesRead += chunk.size
        packetStore.write(chunk)
      }
      return ByteArrayInputStream(packetStore.toByteArray())
    }
  }

  fun updateHeader(rtmpHeader: RtmpHeader) {
    header.basicHeader = rtmpHeader.basicHeader
    header.messageType = rtmpHeader.messageType
    header.messageLength = rtmpHeader.messageLength
    header.messageStreamId = rtmpHeader.messageStreamId
    header.timeStamp = rtmpHeader.timeStamp
  }

  @Throws(IOException::class)
  fun writeHeader(output: OutputStream) {
    header.writeHeader(output)
  }

  @Throws(IOException::class)
  fun writeBody(output: OutputStream) {
    val chunkSize = RtmpConfig.writeChunkSize
    val bytes = storeBody()
    var pos = 0
    var length = getSize()

    while (length > chunkSize) {
      // Write packet for chunk
      output.write(bytes, pos, chunkSize)
      length -= chunkSize
      pos += chunkSize
      // Write header for remain chunk
      header.writeHeader(BasicHeader(ChunkType.TYPE_3, header.basicHeader.chunkStreamId), output)
    }
    output.write(bytes, pos, length)
  }

  abstract fun readBody(input: InputStream)

  abstract fun storeBody(): ByteArray

  abstract fun getType(): MessageType

  abstract fun getSize(): Int
}