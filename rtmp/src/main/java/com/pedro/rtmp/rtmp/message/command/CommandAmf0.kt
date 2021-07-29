package com.pedro.rtmp.rtmp.message.command

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType

/**
 * Created by pedro on 21/04/21.
 */
class CommandAmf0(name: String = "", commandId: Int = 0, timestamp: Int = 0, streamId: Int = 0, basicHeader: BasicHeader =
    BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)): Command(name, commandId, timestamp, streamId, basicHeader = basicHeader) {
  override fun getType(): MessageType = MessageType.COMMAND_AMF0
}