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

package com.pedro.rtmp.utils

import com.pedro.rtmp.rtmp.message.RtmpHeader

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