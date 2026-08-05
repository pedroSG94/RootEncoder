package com.pedro.library.view

import kotlin.math.min

class GlTimestamp {

  companion object {
    private const val DEFAULT_DRIFT = 100_000_000L
  }
  private var drift = DEFAULT_DRIFT
  private var lastSourceTimestamp = 0L
  private var lastClockTimestamp = 0L
  private var currentTimestamp = 0L

  fun setFps(fps: Int) {
    drift = if (fps <= 0) DEFAULT_DRIFT else min(DEFAULT_DRIFT, 1000_000_000L / fps)
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