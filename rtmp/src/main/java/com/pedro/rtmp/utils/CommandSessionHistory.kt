package com.pedro.rtmp.utils

/**
 * Created by pedro on 22/04/21.
 */
class CommandSessionHistory(private val commandHistory: HashMap<Int, String> = HashMap()) {

  fun getName(id: Int): String? {
    return commandHistory[id]
  }

  fun setPacket(id: Int, name: String) {
    commandHistory[id] = name
  }

  fun reset() {
    commandHistory.clear()
  }
}