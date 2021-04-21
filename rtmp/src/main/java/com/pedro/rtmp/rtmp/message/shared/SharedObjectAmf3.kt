package com.pedro.rtmp.rtmp.message.shared

import com.pedro.rtmp.rtmp.message.MessageType

/**
 * Created by pedro on 21/04/21.
 */
class SharedObjectAmf3: SharedObject() {
  override fun getType(): MessageType = MessageType.SHARED_OBJECT_AMF3
}