package com.pedro.library.view

class GlTimestamp {

  private var drift = 20_000_000L
  private var lastSourceTimestamp = 0L
  private var lastClockTimestamp = 0L
  private var currentTimestamp = 0L

  fun setFps(fps: Int) {
    drift = 3000_000_000L / fps //drift of 3 frames.
  }

  fun getTimestamp(sourceTimestamp: Long, clockTimestamp: Long): Long {
    if (lastClockTimestamp == 0L) {
      lastSourceTimestamp = sourceTimestamp
      lastClockTimestamp = clockTimestamp
      currentTimestamp = clockTimestamp
      return currentTimestamp
    }
    val delta = if (sourceTimestamp <= lastSourceTimestamp) {
      clockTimestamp - lastClockTimestamp
    } else sourceTimestamp - lastSourceTimestamp
    lastSourceTimestamp = sourceTimestamp
    lastClockTimestamp = clockTimestamp
    currentTimestamp += delta

    if (currentTimestamp > clockTimestamp + drift) currentTimestamp = clockTimestamp + drift
    else if (currentTimestamp < clockTimestamp - drift) currentTimestamp = clockTimestamp - drift
    return currentTimestamp
  }

  fun reset() {
    lastSourceTimestamp = 0
    lastClockTimestamp = 0
    currentTimestamp = 0
  }
}