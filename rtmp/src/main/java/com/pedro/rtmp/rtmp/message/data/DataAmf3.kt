package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.rtmp.message.MessageType

/**
 * Created by pedro on 21/04/21.
 */
class DataAmf3: Data() {
  override fun getType(): MessageType = MessageType.DATA_AMF3
}