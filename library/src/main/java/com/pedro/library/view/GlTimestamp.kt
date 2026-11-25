package com.pedro.library.view

class GlTimestamp {

  companion object {
    private const val MAX_DRIFT = 500_000_000L
  }
  private var lastSourceTimestamp = 0L
  private var lastClockTimestamp = 0L
  private var currentTimestamp = 0L
  private var forcedAdvanceNs = 0L

  fun setFps(fps: Int) {
    // Keep MAX_DRIFT at 500ms so Camera2 ISP + HandlerThread jitter
    // and sensor-vs-CPU clock drift do not clamp timestamps back to wall clock.
  }

  fun getTimestamp(sourceTimestamp: Long, clockTimestamp: Long): Long {
    if (lastClockTimestamp == 0L) {
      lastSourceTimestamp = sourceTimestamp
      lastClockTimestamp = clockTimestamp
      currentTimestamp = clockTimestamp
      forcedAdvanceNs = 0L
      return currentTimestamp
    }
    val clockDelta = (clockTimestamp - lastClockTimestamp).coerceAtLeast(0L)
    val delta = if (sourceTimestamp <= lastSourceTimestamp) {
      forcedAdvanceNs += clockDelta
      clockDelta
    } else {
      val sourceDelta = sourceTimestamp - lastSourceTimestamp
      lastSourceTimestamp = sourceTimestamp
      if (forcedAdvanceNs > 0L) {
        val adjusted = (sourceDelta - forcedAdvanceNs).coerceAtLeast(0L)
        forcedAdvanceNs = 0L
        adjusted
      } else {
        sourceDelta
      }
    }
    lastClockTimestamp = clockTimestamp
    currentTimestamp += delta

    if (currentTimestamp > clockTimestamp + MAX_DRIFT) currentTimestamp = clockTimestamp + MAX_DRIFT
    else if (currentTimestamp < clockTimestamp - MAX_DRIFT) currentTimestamp = clockTimestamp - MAX_DRIFT
    return currentTimestamp
  }

  fun reset() {
    lastSourceTimestamp = 0L
    lastClockTimestamp = 0L
    currentTimestamp = 0L
    forcedAdvanceNs = 0L
  }
}