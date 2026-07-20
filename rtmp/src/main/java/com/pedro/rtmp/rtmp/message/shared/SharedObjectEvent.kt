package com.pedro.rtmp.rtmp.message.shared

import com.pedro.rtmp.amf.AmfData

data class SharedObjectEvent(
  val type: SharedObjectEventType,
  val data: LinkedHashMap<String, AmfData>
)