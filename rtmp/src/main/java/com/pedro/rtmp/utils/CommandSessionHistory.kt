package com.pedro.rtmp.utils

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.message.RtmpHeader
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by pedro on 22/04/21.
 */
class CommandSessionHistory(private val commandHistory: HashMap<Int, String> = HashMap(),
    private val headerHistory: MutableList<RtmpHeader> = ArrayList()) {

  fun setReadHeader(header: RtmpHeader) {
    headerHistory.add(header)
  }

  fun getLastReadHeader(chunkStreamId: Int): RtmpHeader? {
    val reverseList = headerHistory
    reverseList.reversed().forEach {
      if (it.basicHeader.chunkStreamId == chunkStreamId) {
        return it
      }
    }
    return null
  }

  fun getName(id: Int): String? {
    return commandHistory[id]
  }

  fun setPacket(id: Int, name: String) {
    commandHistory[id] = name
  }

  fun reset() {
    commandHistory.clear()
    headerHistory.clear()
  }
}