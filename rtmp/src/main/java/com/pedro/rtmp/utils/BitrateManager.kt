package com.pedro.rtmp.utils

/**
 * Created by pedro on 8/04/21.
 *
 * Calculate video and audio bitrate per second
 */
open class BitrateManager(private val connectCheckerRtmp: ConnectCheckerRtmp) {

  private var bitrate: Long = 0
  private var timeStamp = System.currentTimeMillis()

  @Synchronized
  fun calculateBitrate(size: Long) {
    bitrate += size
    val timeDiff = System.currentTimeMillis() - timeStamp
    if (timeDiff >= 1000) {
      connectCheckerRtmp.onNewBitrateRtmp((bitrate / (timeDiff / 1000f)).toLong())
      timeStamp = System.currentTimeMillis()
      bitrate = 0
    }
  }
}