package com.pedro.rtmp.rtmp.message.command

import com.pedro.rtmp.rtmp.message.MessageType

/**
 * Created by pedro on 21/04/21.
 */
class CommandAmf0(name: String = "", transactionId: Int = 0): Command(name, transactionId) {
  override fun getType(): MessageType = MessageType.COMMAND_AMF0
}