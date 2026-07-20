package com.pedro.rtmp.rtmp.message.shared

enum class SharedObjectEventType(val value: Int) {
  USE(1), RELEASE(2), REQUEST_CHANGE(3), CHANGE(4),
  SUCCESS(5), SEND_MESSAGE(6), STATUS(7), CLEAR(8), REMOVE(9),
  REQUEST_REMOVE(10), USE_SUCCESS(11), UNKNOWN(255);
}