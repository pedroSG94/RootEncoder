package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType

/**
 * Created by pedro on 21/04/21.
 */
class DataAmf0(name: String = "", timeStamp: Int = 0, streamId: Int = 0, basicHeader: BasicHeader = BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)):
    Data(name, timeStamp, streamId, basicHeader) {
  override fun getType(): MessageType = MessageType.DATA_AMF0
}